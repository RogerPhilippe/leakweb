// Web Components. Light DOM, so the page stylesheet applies.

import { formatBytes, h, render, simpleClassName } from './dom.js';

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

customElements.define('lw-leak-trace', LeakTraceElement);
customElements.define('lw-toasts', ToastsElement);
