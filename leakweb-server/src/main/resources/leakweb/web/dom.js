// DOM helpers. Every text goes through text nodes (never parsed as HTML): class names, field names and
// values come from the app's heap and must not be interpreted as markup.

/**
 * Creates an element. `attrs` keys: `class`, `text`, `on<Event>` (listener), `dataset` (object),
 * boolean values toggle the attribute, anything else is set with setAttribute.
 */
export function h(tag, attrs = {}, ...children) {
  const element = document.createElement(tag);
  for (const [name, value] of Object.entries(attrs)) {
    if (value === null || value === undefined || value === false) continue;
    if (name === 'class') element.className = value;
    else if (name === 'text') element.textContent = String(value);
    else if (name === 'dataset') Object.assign(element.dataset, value);
    else if (name.startsWith('on') && typeof value === 'function') {
      element.addEventListener(name.slice(2).toLowerCase(), value);
    } else element.setAttribute(name, value === true ? '' : String(value));
  }
  append(element, children);
  return element;
}

const SVG_NAMESPACE = 'http://www.w3.org/2000/svg';

/** Creates an SVG element. `attrs` keys: `class`, `text`, anything else is set with setAttribute. */
export function svg(tag, attrs = {}, ...children) {
  const element = document.createElementNS(SVG_NAMESPACE, tag);
  for (const [name, value] of Object.entries(attrs)) {
    if (value === null || value === undefined || value === false) continue;
    if (name === 'text') element.textContent = String(value);
    else element.setAttribute(name, String(value));
  }
  append(element, children);
  return element;
}

function append(parent, children) {
  for (const child of children.flat(Infinity)) {
    if (child === null || child === undefined || child === false) continue;
    parent.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
}

/** Replaces the content of `parent`. */
export function render(parent, ...children) {
  parent.replaceChildren();
  append(parent, children);
}

export function formatBytes(bytes) {
  if (bytes === null || bytes === undefined) return '—';
  const units = ['B', 'KB', 'MB', 'GB'];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value.toFixed(unit === 0 || value >= 100 ? 0 : 1)} ${units[unit]}`;
}

export function formatDuration(millis) {
  if (millis < 1000) return `${millis} ms`;
  const seconds = millis / 1000;
  if (seconds < 60) return `${seconds.toFixed(1)} s`;
  return `${Math.floor(seconds / 60)} min ${Math.round(seconds % 60)} s`;
}

const relativeFormat = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });

export function formatRelative(timestampMillis) {
  const seconds = Math.round((timestampMillis - Date.now()) / 1000);
  const steps = [
    [60, 'second'],
    [60, 'minute'],
    [24, 'hour'],
    [Infinity, 'day'],
  ];
  let value = seconds;
  for (const [size, unit] of steps) {
    if (Math.abs(value) < size) return relativeFormat.format(value, unit);
    value = Math.round(value / size);
  }
  return '';
}

export function formatTime(timestampMillis) {
  return new Date(timestampMillis).toLocaleTimeString();
}

export function formatDate(timestampMillis) {
  return new Date(timestampMillis).toLocaleString();
}

/** `com.example.foo.Bar$Inner` -> `Bar$Inner` */
export function simpleClassName(className) {
  return className.slice(className.lastIndexOf('.') + 1);
}

/** Section shown while the data of a view loads or when it failed. */
export function message(text, kind = 'empty') {
  return h('p', { class: `message ${kind}`, text });
}
