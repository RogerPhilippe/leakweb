// Web Components. Light DOM, so the page stylesheet applies.

import { formatBytes, formatTime, h, render, simpleClassName, svg } from './dom.js';
import { dumpProgress } from './dump-progress.js';

const STATUS_LABELS = { LEAKING: 'Leaking', NOT_LEAKING: 'Not leaking', UNKNOWN: 'Unknown' };

/** Shows a leak trace: GC root, then each object and the reference to the next, down to the leaking object. */
class LeakTraceElement extends HTMLElement {
  set trace(trace) {
    this._trace = trace;
    this.update();
  }

  connectedCallback() {
    this.update();
  }

  update() {
    const trace = this._trace;
    if (!trace || !this.isConnected) return;
    const items = [h('li', { class: 'trace-root' }, h('span', { class: 'chip', text: 'GC root' }), trace.gcRootType)];
    for (const reference of trace.referencePath) {
      items.push(objectNode(reference.originObject));
      items.push(referenceEdge(reference));
    }
    items.push(objectNode(trace.leakingObject, true));
    render(this, h('ol', { class: 'trace' }, items));
  }
}

function objectNode(object, isLeakingObject = false) {
  const className = object.className;
  const packageName = className.includes('.') ? className.slice(0, className.lastIndexOf('.')) : '';
  const status = object.leakingStatus;
  return h(
    'li',
    { class: `trace-node node-${status.toLowerCase()}${isLeakingObject ? ' leaking-object' : ''}` },
    h(
      'div',
      { class: 'node-head' },
      h('span', { class: 'object-type', text: object.objectType.toLowerCase() }),
      h('span', { class: 'class-name', title: className, text: simpleClassName(className) }),
      packageName && h('span', { class: 'package-name', text: packageName }),
      h('span', { class: `status status-${status.toLowerCase()}`, text: STATUS_LABELS[status] ?? status }),
    ),
    object.leakingStatusReason && h('p', { class: 'reason', text: object.leakingStatusReason }),
    object.retainedHeapByteSize != null &&
      h('p', {
        class: 'retained',
        text: `Retains ${formatBytes(object.retainedHeapByteSize)} in ${object.retainedObjectCount ?? '?'} objects`,
      }),
    object.labels.length > 0 && h('ul', { class: 'labels' }, object.labels.map((label) => h('li', { text: label }))),
  );
}

function referenceEdge(reference) {
  return h(
    'li',
    { class: `trace-edge${reference.isSuspect ? ' suspect' : ''}` },
    h('span', { class: 'arrow', text: '↓' }),
    h('code', { text: referenceLabel(reference) }),
    h('span', { class: 'reference-type', text: reference.referenceType.toLowerCase().replace('_', ' ') }),
    reference.isSuspect && h('span', { class: 'chip suspect-chip', text: 'suspect' }),
  );
}

function referenceLabel(reference) {
  const owner = simpleClassName(reference.owningClassName);
  switch (reference.referenceType) {
    case 'STATIC_FIELD':
      return `static ${owner}.${reference.referenceDisplayName}`;
    case 'INSTANCE_FIELD':
      return `${owner}.${reference.referenceDisplayName}`;
    case 'ARRAY_ENTRY':
      // Shark already names array entries `[index]`.
      return `${owner}${reference.referenceDisplayName}`;
    default:
      return reference.referenceDisplayName;
  }
}

/** Stack of short notifications; `show()` adds one that disappears after a few seconds. */
class ToastsElement extends HTMLElement {
  connectedCallback() {
    this.setAttribute('role', 'status');
    this.setAttribute('aria-live', 'polite');
  }

  show(text, { href, kind = 'info', durationMillis = 6000 } = {}) {
    const toast = h('div', { class: `toast ${kind}` }, href ? h('a', { href, text }) : text);
    this.append(toast);
    setTimeout(() => toast.remove(), durationMillis);
  }
}

const CHART_WINDOW_MILLIS = 5 * 60 * 1000;
const CHART_PADDING = { top: 10, right: 12, bottom: 22, left: 64 };
const CHART_GRID_LINES = 4;
const CHART_SERIES = [
  { label: 'Java heap', className: 'series-java', value: (sample) => sample.javaHeapUsedBytes },
  { label: 'Native heap', className: 'series-native', value: (sample) => sample.nativeHeapAllocatedBytes },
  { label: 'PSS', className: 'series-pss', value: (sample) => (sample.pssKb == null ? null : sample.pssKb * 1024) },
];

/**
 * Live memory chart over the last 5 minutes, drawn as SVG at the element's size. `samples` sets the
 * history, `add()` appends a sample. Hovering shows the values at that time in the legend.
 */
class MemoryChartElement extends HTMLElement {
  #samples = [];
  #hovered = null;
  #resizeObserver = new ResizeObserver(() => this.update());

  set samples(samples) {
    this.#samples = [...samples];
    this.update();
  }

  add(sample) {
    this.#samples.push(sample);
    const oldest = sample.timestampMillis - CHART_WINDOW_MILLIS;
    while (this.#samples.length > 0 && this.#samples[0].timestampMillis < oldest) this.#samples.shift();
    this.update();
  }

  connectedCallback() {
    if (!this.chart) {
      this.chart = svg('svg', { class: 'chart', role: 'img', 'aria-label': 'Memory usage over the last 5 minutes' });
      this.legend = h('ul', { class: 'chart-legend' });
      this.append(this.chart, this.legend);
      this.chart.addEventListener('pointermove', (event) => this.#hover(event));
      this.chart.addEventListener('pointerleave', () => {
        this.#hovered = null;
        this.update();
      });
    }
    this.#resizeObserver.observe(this);
    this.update();
  }

  disconnectedCallback() {
    this.#resizeObserver.disconnect();
  }

  #geometry() {
    const width = this.chart.clientWidth;
    const height = this.chart.clientHeight;
    const latest = this.#samples.at(-1)?.timestampMillis ?? Date.now();
    const values = this.#samples.flatMap((sample) => CHART_SERIES.map((series) => series.value(sample) ?? 0));
    const maxValue = niceCeiling(Math.max(1, ...values) * 1.1);
    const plotWidth = Math.max(0, width - CHART_PADDING.left - CHART_PADDING.right);
    const plotHeight = Math.max(0, height - CHART_PADDING.top - CHART_PADDING.bottom);
    return {
      width,
      height,
      latest,
      maxValue,
      x: (timestamp) => CHART_PADDING.left + plotWidth * (1 - (latest - timestamp) / CHART_WINDOW_MILLIS),
      y: (value) => CHART_PADDING.top + plotHeight * (1 - value / maxValue),
      timeAt: (x) => latest - (1 - (x - CHART_PADDING.left) / plotWidth) * CHART_WINDOW_MILLIS,
    };
  }

  #hover(event) {
    if (this.#samples.length === 0) return;
    const time = this.#geometry().timeAt(event.offsetX);
    this.#hovered = this.#samples.reduce((best, sample) =>
      Math.abs(sample.timestampMillis - time) < Math.abs(best.timestampMillis - time) ? sample : best,
    );
    this.update();
  }

  update() {
    if (!this.chart || !this.isConnected) return;
    const geometry = this.#geometry();
    const { width, height, x, y } = geometry;
    this.chart.setAttribute('viewBox', `0 0 ${width} ${height}`);

    const grid = [];
    for (let line = 0; line <= CHART_GRID_LINES; line++) {
      const value = (geometry.maxValue * line) / CHART_GRID_LINES;
      grid.push(
        svg('line', { class: 'chart-grid', x1: CHART_PADDING.left, x2: width - CHART_PADDING.right, y1: y(value), y2: y(value) }),
        svg('text', { class: 'chart-label', x: CHART_PADDING.left - 8, y: y(value), 'text-anchor': 'end', 'dominant-baseline': 'middle', text: formatBytes(value) }),
      );
    }
    for (const [label, minutesAgo] of [['−5 min', 5], ['−2.5 min', 2.5], ['now', 0]]) {
      const anchor = minutesAgo === 5 ? 'start' : minutesAgo === 0 ? 'end' : 'middle';
      grid.push(svg('text', { class: 'chart-label', x: x(geometry.latest - minutesAgo * 60_000), y: height - 4, 'text-anchor': anchor, text: label }));
    }

    const lines = CHART_SERIES.map((series) => svg('path', { class: `chart-line ${series.className}`, d: linePath(this.#samples, series, x, y) }));

    const hovered = this.#hovered;
    const marker = hovered && [
      svg('line', { class: 'chart-cursor', x1: x(hovered.timestampMillis), x2: x(hovered.timestampMillis), y1: CHART_PADDING.top, y2: height - CHART_PADDING.bottom }),
      CHART_SERIES.map((series) => {
        const value = series.value(hovered);
        return value != null && svg('circle', { class: `chart-dot ${series.className}`, cx: x(hovered.timestampMillis), cy: y(value), r: 3.5 });
      }),
    ];
    render(this.chart, grid, lines, marker);

    const shown = hovered ?? this.#samples.at(-1);
    render(
      this.legend,
      CHART_SERIES.map((series) =>
        h('li', {}, h('span', { class: `swatch ${series.className}` }), series.label, ' ', h('strong', { text: shown ? formatBytes(series.value(shown)) : '—' })),
      ),
      shown && h('li', { class: 'muted', text: hovered ? formatTime(hovered.timestampMillis) : 'now' }),
    );
  }
}

/** SVG path of one series; a gap (no value) starts a new segment. */
function linePath(samples, series, x, y) {
  let path = '';
  let drawing = false;
  for (const sample of samples) {
    const value = series.value(sample);
    if (value == null) {
      drawing = false;
      continue;
    }
    path += `${drawing ? 'L' : 'M'}${x(sample.timestampMillis).toFixed(1)},${y(value).toFixed(1)}`;
    drawing = true;
  }
  return path;
}

/** Rounds up to a value whose quarters (the grid lines) are round numbers of KB, MB or GB. */
function niceCeiling(value) {
  const unit = 1024 ** Math.floor(Math.log(value) / Math.log(1024));
  const scaled = value / unit;
  const magnitude = 10 ** Math.floor(Math.log10(scaled));
  const step = [1, 1.2, 1.6, 2, 4, 6, 8, 10].find((candidate) => candidate * magnitude >= scaled);
  return step * magnitude * unit;
}

/**
 * Button that requests a heap dump (`leakweb:dump-request` event, handled by app.js). While a dump or
 * its analysis runs, a progress bar takes its place, in every dump button of the page.
 */
class DumpButtonElement extends HTMLElement {
  #unsubscribe = null;

  connectedCallback() {
    this.#unsubscribe = dumpProgress.subscribe(() => this.update());
    this.update();
  }

  disconnectedCallback() {
    this.#unsubscribe?.();
  }

  update() {
    const state = dumpProgress.get();
    if (state.phase === 'idle') {
      render(
        this,
        h('button', {
          type: 'button',
          class: 'primary',
          text: this.getAttribute('label') ?? 'Dump heap',
          onClick: () => this.dispatchEvent(new CustomEvent('leakweb:dump-request', { bubbles: true })),
        }),
      );
      return;
    }
    const analyzing = state.phase === 'analyzing';
    const label = analyzing ? `Analyzing: ${state.step.toLowerCase().replaceAll('_', ' ')}` : 'Dumping heap…';
    render(
      this,
      h(
        'span',
        { class: 'dump-progress', role: 'status', title: label },
        // No value: indeterminate while the app is frozen by the dump.
        h('progress', { max: 100, value: analyzing ? state.percent : null }),
        h('span', { class: 'dump-progress-label', text: label }),
      ),
    );
  }
}

customElements.define('lw-dump-button', DumpButtonElement);
customElements.define('lw-leak-trace', LeakTraceElement);
customElements.define('lw-memory-chart', MemoryChartElement);
customElements.define('lw-toasts', ToastsElement);
