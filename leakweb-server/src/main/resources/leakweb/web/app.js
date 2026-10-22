// Entry point: hash router, header actions and live events (Server-Sent Events).

import { api } from './api.js';
import './components.js';
import { message, render } from './dom.js';
import { analysesView, leakDetailView, leaksView, memoryView, retainedView } from './views.js';

const ANALYSIS_STEPS = 10;
const RECONNECT_DELAY_MILLIS = 5000;
const THEMES = ['auto', 'light', 'dark'];

const view = document.getElementById('view');
const toasts = document.querySelector('lw-toasts');
const live = document.getElementById('live');
const liveLabel = document.getElementById('live-label');
const liveProgress = document.getElementById('live-progress');
const connection = document.getElementById('connection');
const retainedCount = document.getElementById('retained-count');
const leakCount = document.getElementById('leak-count');

const routes = [
  { pattern: /^#\/leaks\/([A-Za-z0-9_-]+)$/, section: 'leaks', show: (params) => leakDetailView(view, params[0]) },
  { pattern: /^#\/leaks$/, section: 'leaks', show: () => leaksView(view) },
  { pattern: /^#\/retained$/, section: 'retained', show: () => retainedView(view, { onDumpHeap: dumpHeap }) },
  { pattern: /^#\/memory$/, section: 'memory', show: () => memoryView(view) },
  { pattern: /^#\/analyses$/, section: 'analyses', show: () => analysesView(view, { onChanged: refresh }) },
];

let cleanup = null;
let navigation = 0;

async function showRoute() {
  const hash = location.hash || '#/leaks';
  const route = routes.find((candidate) => candidate.pattern.test(hash));
  if (!route) {
    location.replace('#/leaks');
    return;
  }
  for (const link of document.querySelectorAll('.sidebar a')) {
    link.toggleAttribute('aria-current', link.dataset.section === route.section);
  }
  cleanup?.();
  cleanup = null;
  const current = ++navigation;
  view.setAttribute('aria-busy', 'true');
  try {
    const result = await route.show(hash.match(route.pattern).slice(1));
    // A newer navigation started meanwhile: its view wins.
    if (current === navigation) cleanup = typeof result === 'function' ? result : null;
    else if (typeof result === 'function') result();
  } catch (error) {
    // A 401 is shown by the leakweb:unauthorized handler.
    if (current === navigation && error.status !== 401) {
      render(view, message(error.status === 404 ? 'Not found. It may have been deleted.' : error.message, 'error'));
    }
  } finally {
    view.removeAttribute('aria-busy');
  }
}

/** Re-renders the current view and the counters, e.g. after an analysis finished. */
function refresh() {
  showRoute();
  updateCounters();
}

async function updateCounters() {
  try {
    const [status, leaks] = await Promise.all([api.status(), api.leaks()]);
    document.getElementById('app-name').textContent = status.appPackage;
    document.title = `LeakWeb · ${status.appPackage}`;
    setBadge(retainedCount, status.retainedObjectCount);
    setBadge(leakCount, leaks.length);
  } catch {
    // The unauthorized handler or the view shows the error.
  }
}

function setBadge(badge, count) {
  badge.textContent = count > 0 ? String(count) : '';
}

async function dumpHeap() {
  try {
    const result = await api.heapDump();
    toasts.show(result.accepted ? 'Heap dump requested' : result.message);
  } catch (error) {
    toasts.show(error.message, { kind: 'error' });
  }
}

async function runGc() {
  try {
    await api.gc();
    toasts.show('Garbage collection run');
    updateCounters();
  } catch (error) {
    toasts.show(error.message, { kind: 'error' });
  }
}

function showProgress(label, percent) {
  live.hidden = false;
  liveLabel.textContent = label;
  if (percent === null) liveProgress.removeAttribute('value');
  else liveProgress.value = percent;
}

function connectEvents() {
  const events = new EventSource('/api/events');
  events.addEventListener('open', () => setConnection('Connected', true));
  events.addEventListener('error', () => {
    setConnection('Reconnecting', false);
    // The browser gives up after an HTTP error (e.g. the app restarted); keep trying.
    if (events.readyState === EventSource.CLOSED) setTimeout(connectEvents, RECONNECT_DELAY_MILLIS);
  });
  const on = (type, handler) => events.addEventListener(type, (event) => handler(JSON.parse(event.data)));

  on('object.retained', (data) => {
    setBadge(retainedCount, data.retainedObjectCount);
    if (location.hash === '#/retained') showRoute();
  });
  // Views that show live memory listen to this DOM event.
  on('memory.sample', (data) => document.dispatchEvent(new CustomEvent('leakweb:memory-sample', { detail: data.sample })));
  on('heapdump.started', () => showProgress('Dumping heap…', null));
  on('analysis.progress', (data) => {
    // The dump is written: the objects it holds are no longer watched.
    if (data.step === 'PARSING_HEAP_DUMP') {
      updateCounters();
      if (location.hash === '#/retained') showRoute();
    }
    const step = data.step.toLowerCase().replaceAll('_', ' ');
    showProgress(`Analyzing: ${step}`, data.percent ?? (100 / ANALYSIS_STEPS));
  });
  on('analysis.done', (data) => {
    live.hidden = true;
    if (!data.success) toasts.show('Heap analysis failed, see Analyses', { href: '#/analyses', kind: 'error' });
    refresh();
  });
  on('leak.new', (data) => {
    toasts.show(`New leak: ${data.shortDescription}`, { href: `#/leaks/${encodeURIComponent(data.signature)}`, kind: 'leak' });
  });
}

function setConnection(text, ok) {
  connection.textContent = text;
  connection.classList.toggle('ok', ok);
}

function applyTheme(theme) {
  if (theme === 'auto') document.documentElement.removeAttribute('data-theme');
  else document.documentElement.dataset.theme = theme;
  const button = document.getElementById('theme-button');
  button.title = `Theme: ${theme}`;
  button.setAttribute('aria-label', `Theme: ${theme}`);
}

function setupTheme() {
  let theme = 'auto';
  try {
    theme = localStorage.getItem('leakweb-theme') ?? 'auto';
  } catch {
    // Storage blocked: the theme is not remembered.
  }
  applyTheme(theme);
  document.getElementById('theme-button').addEventListener('click', () => {
    theme = THEMES[(THEMES.indexOf(theme) + 1) % THEMES.length];
    applyTheme(theme);
    try {
      localStorage.setItem('leakweb-theme', theme);
    } catch {
      // Not remembered.
    }
  });
}

document.addEventListener('leakweb:unauthorized', () => {
  render(view, message('Session expired or invalid token. Open the URL with the token printed in logcat (tag LeakWeb).', 'error'));
});
document.getElementById('gc-button').addEventListener('click', runGc);
document.getElementById('dump-button').addEventListener('click', dumpHeap);
window.addEventListener('hashchange', showRoute);

setupTheme();
showRoute();
updateCounters();
connectEvents();
