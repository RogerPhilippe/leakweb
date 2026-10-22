// The views of the main area. Each one renders into `container` and may return a cleanup function.

import { api } from './api.js';
import {
  formatBytes,
  formatDate,
  formatDuration,
  formatRelative,
  h,
  message,
  render,
  simpleClassName,
} from './dom.js';

const CONFIRM_TIMEOUT_MILLIS = 3000;

const SORTS = {
  retained: { label: 'Retained size', compare: (a, b) => (b.retainedHeapByteSize ?? -1) - (a.retainedHeapByteSize ?? -1) },
  lastSeen: { label: 'Last seen', compare: (a, b) => b.lastSeenMillis - a.lastSeenMillis },
  occurrences: { label: 'Occurrences', compare: (a, b) => b.occurrenceCount - a.occurrenceCount },
};

// Survives navigation between views, so going back to the list keeps the search.
const leakListState = { query: '', sort: 'retained' };

export async function leaksView(container) {
  const leaks = await api.leaks();
  if (leaks.length === 0) {
    render(
      container,
      h('h1', { text: 'Leaks' }),
      message('No leaks found yet. Leaks appear here after a heap dump is analyzed.'),
    );
    return;
  }
  const list = h('ul', { class: 'card-list' });
  const update = () => {
    const query = leakListState.query.trim().toLowerCase();
    const visible = leaks
      .filter((leak) => !query || `${leak.shortDescription} ${leak.leakingClassName}`.toLowerCase().includes(query))
      .sort(SORTS[leakListState.sort].compare);
    render(list, visible.length ? visible.map(leakCard) : h('li', {}, message('No leak matches the search.')));
  };
  const search = h('input', {
    type: 'search',
    placeholder: 'Search by class or description',
    'aria-label': 'Search leaks',
    value: leakListState.query,
    onInput: (event) => {
      leakListState.query = event.target.value;
      update();
    },
  });
  const sort = h(
    'select',
    {
      'aria-label': 'Sort leaks',
      onChange: (event) => {
        leakListState.sort = event.target.value;
        update();
      },
    },
    Object.entries(SORTS).map(([key, { label }]) =>
      h('option', { value: key, selected: key === leakListState.sort, text: `Sort: ${label}` }),
    ),
  );
  render(container, h('h1', { text: 'Leaks' }), h('div', { class: 'toolbar' }, search, sort), list);
  update();
}

function leakCard(leak) {
  return h(
    'li',
    {},
    h(
      'a',
      { class: 'card leak-card', href: `#/leaks/${encodeURIComponent(leak.signature)}` },
      h(
        'div',
        { class: 'card-main' },
        h('strong', { class: 'class-name', text: simpleClassName(leak.leakingClassName) }),
        leak.kind === 'LIBRARY' && h('span', { class: 'chip library', text: 'library' }),
        h('p', { class: 'muted', text: leak.shortDescription }),
      ),
      h(
        'dl',
        { class: 'card-stats' },
        stat('Retained', formatBytes(leak.retainedHeapByteSize)),
        stat('Occurrences', leak.occurrenceCount),
        stat('Last seen', formatRelative(leak.lastSeenMillis)),
      ),
    ),
  );
}

function stat(label, value) {
  return h('div', {}, h('dt', { text: label }), h('dd', { text: value }));
}

export async function leakDetailView(container, signature) {
  const { summary, latest, analysisIds } = await api.leak(signature);
  const traces = latest.traces;
  render(
    container,
    h('a', { class: 'back', href: '#/leaks', text: '← Leaks' }),
    h('h1', { class: 'class-name', title: summary.leakingClassName, text: simpleClassName(summary.leakingClassName) }),
    h('p', { class: 'muted', text: summary.leakingClassName }),
    h('p', { class: 'lead', text: summary.shortDescription }),
    latest.libraryLeakDescription && h('p', { class: 'notice', text: `Known library leak: ${latest.libraryLeakDescription}` }),
    h(
      'dl',
      { class: 'card-stats wide' },
      stat('Retained', formatBytes(summary.retainedHeapByteSize)),
      stat('Occurrences', summary.occurrenceCount),
      stat('First seen', formatDate(summary.firstSeenMillis)),
      stat('Last seen', formatDate(summary.lastSeenMillis)),
    ),
    h(
      'details',
      { class: 'analyses-of-leak' },
      h('summary', { text: analysisIds.length === 1 ? 'Found in 1 analysis' : `Found in ${analysisIds.length} analyses` }),
      h(
        'ul',
        {},
        analysisIds.map((id) => h('li', {}, h('a', { href: api.heapDumpUrl(id), download: `${id}.hprof`, text: `${id}.hprof` }))),
      ),
    ),
    traces.map((trace, index) => [
      h('h2', { text: traces.length > 1 ? `Leak trace ${index + 1} of ${traces.length}` : 'Leak trace' }),
      h('p', { class: 'muted', text: 'From the GC root down to the leaking object. Suspect references are highlighted.' }),
      Object.assign(document.createElement('lw-leak-trace'), { trace }),
    ]),
  );
}

export async function retainedView(container) {
  const retained = await api.retained();
  render(
    container,
    h('h1', { text: 'Retained objects' }),
    h(
      'p',
      { class: 'muted' },
      'Objects still in memory after their lifecycle ended, not analyzed yet. ',
      'The heap is dumped automatically when enough of them pile up.',
    ),
    h('div', { class: 'toolbar' }, h('lw-dump-button', { label: 'Dump heap now' })),
    retained.length === 0
      ? message('Nothing retained right now.')
      : h(
          'ul',
          { class: 'card-list' },
          retained.map((object) =>
            h(
              'li',
              { class: 'card' },
              h(
                'div',
                { class: 'card-main' },
                h('strong', { class: 'class-name', title: object.className, text: simpleClassName(object.className) }),
                h('p', { class: 'muted', text: object.description }),
              ),
              h(
                'dl',
                { class: 'card-stats' },
                stat('Retained after', formatDuration(object.retainedUptimeMillis - object.watchUptimeMillis)),
                stat('Key', object.key.slice(0, 8)),
              ),
            ),
          ),
        ),
  );
}

/**
 * Live memory is off by default. The toggle turns sampling on or off in the app itself: it keeps
 * running after this page is closed, until it is turned off. Starting it clears the chart.
 */
export async function memoryView(container) {
  const cards = h('div', { class: 'memory-grid' });
  const chart = document.createElement('lw-memory-chart');
  const chartCard = h('section', { class: 'card chart-card' }, h('h2', { text: 'Last 5 minutes' }), chart);
  const state = h('p', { class: 'live-state' });
  let live = false;
  const toggle = h('button', {
    type: 'button',
    onClick: async () => {
      toggle.disabled = true;
      try {
        await (live ? api.stopLiveMemory() : api.startLiveMemory());
        await load();
      } catch (error) {
        state.textContent = error.message;
      } finally {
        toggle.disabled = false;
      }
    },
  });

  const showSample = (sample) => {
    const usedPercent = Math.round((sample.javaHeapUsedBytes / sample.javaHeapMaxBytes) * 100);
    render(
      cards,
      memoryCard(
        'Java heap',
        `${formatBytes(sample.javaHeapUsedBytes)} of ${formatBytes(sample.javaHeapMaxBytes)}`,
        h('meter', { min: 0, max: 100, low: 60, high: 85, optimum: 0, value: usedPercent, text: `${usedPercent}%` }),
      ),
      memoryCard('Native heap', formatBytes(sample.nativeHeapAllocatedBytes)),
      sample.pssKb != null && memoryCard('PSS', formatBytes(sample.pssKb * 1024)),
      memoryCard('Watched objects', sample.watchedObjectCount),
      memoryCard('Retained objects', sample.retainedObjectCount),
    );
  };

  const load = async () => {
    const [status, history] = await Promise.all([api.status(), api.memoryHistory()]);
    live = status.liveMemoryEnabled;
    toggle.textContent = live ? 'Stop live monitoring' : 'Start live monitoring';
    toggle.className = live ? 'danger' : 'primary';
    toggle.setAttribute('aria-pressed', String(live));
    state.classList.toggle('on', live);
    if (live) {
      state.textContent =
        'Live: one sample per second (PSS every 10 s). It keeps running in the app, even with this page closed ' +
        'or after an app restart, until you stop it.';
    } else if (history.length > 0) {
      state.textContent = 'Stopped. The chart keeps the samples collected until then; starting again clears it.';
    } else {
      state.textContent = 'Stopped: nothing is sampled. The values below were read when the page opened.';
    }
    chart.samples = history;
    chartCard.hidden = !live && history.length === 0;
    showSample((live && history.at(-1)) || (await api.memory()));
  };

  render(
    container,
    h('div', { class: 'view-head' }, h('h1', { text: 'Memory' }), toggle),
    h(
      'p',
      { class: 'muted' },
      'Memory of the app process: Java heap, native heap and PSS (the whole process memory, with shared pages ',
      'split between the processes using them).',
    ),
    state,
    cards,
    chartCard,
  );
  await load();

  // Samples and on/off changes (maybe from another tab) come from the live events (app.js).
  const onSample = (event) => {
    if (!live) return;
    showSample(event.detail);
    chart.add(event.detail);
  };
  const onLiveChanged = (event) => {
    if (event.detail !== live) load().catch(() => {});
  };
  document.addEventListener('leakweb:memory-sample', onSample);
  document.addEventListener('leakweb:memory-live', onLiveChanged);
  return () => {
    document.removeEventListener('leakweb:memory-sample', onSample);
    document.removeEventListener('leakweb:memory-live', onLiveChanged);
  };
}

function memoryCard(label, value, extra) {
  return h('section', { class: 'card memory-card' }, h('h2', { text: label }), h('p', { class: 'big', text: value }), extra);
}

export async function analysesView(container, { onChanged }) {
  const analyses = await api.analyses();
  if (analyses.length === 0) {
    render(container, h('h1', { text: 'Analyses' }), message('No heap dump analyzed yet.'));
    return;
  }
  render(
    container,
    h('h1', { text: 'Analyses' }),
    h(
      'div',
      { class: 'table-wrap' },
      h(
        'table',
        {},
        h(
          'thead',
          {},
          h('tr', {}, ['Date', 'Result', 'Duration', 'Heap dump', ''].map((title) => h('th', { scope: 'col', text: title }))),
        ),
        h('tbody', {}, analyses.map((analysis) => analysisRow(analysis, onChanged))),
      ),
    ),
  );
}

function analysisRow(analysis, onChanged) {
  const result = analysis.success
    ? h('span', { class: 'chip ok', text: analysis.leakCount === 1 ? '1 leak' : `${analysis.leakCount} leaks` })
    : h('span', { class: 'chip failed', title: analysis.errorMessage ?? '', text: 'failed' });
  return h(
    'tr',
    {},
    h('td', { text: formatDate(analysis.createdAtMillis) }),
    h('td', {}, result, !analysis.success && h('p', { class: 'muted small', text: analysis.errorMessage ?? '' })),
    h('td', { text: formatDuration(analysis.durationMillis) }),
    h(
      'td',
      {},
      analysis.heapDumpAvailable
        ? h('a', { href: api.heapDumpUrl(analysis.id), download: `${analysis.id}.hprof`, text: `Download (${formatBytes(analysis.heapDumpSizeBytes)})` })
        : h('span', { class: 'muted', text: 'Deleted' }),
    ),
    h('td', {}, deleteButton(analysis.id, onChanged)),
  );
}

/** Asks for a second click instead of a blocking confirm() dialog. */
function deleteButton(id, onChanged) {
  let armedTimer = null;
  const button = h('button', {
    type: 'button',
    class: 'danger',
    text: 'Delete',
    onClick: async () => {
      if (!armedTimer) {
        button.textContent = 'Confirm delete';
        armedTimer = setTimeout(() => {
          armedTimer = null;
          button.textContent = 'Delete';
        }, CONFIRM_TIMEOUT_MILLIS);
        return;
      }
      clearTimeout(armedTimer);
      button.disabled = true;
      await api.deleteAnalysis(id);
      onChanged();
    },
  });
  return button;
}
