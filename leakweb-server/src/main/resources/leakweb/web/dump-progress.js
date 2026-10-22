// Progress of the heap dump, shared by every dump button. The dump freezes the whole app, web server
// included, so the page sets "dumping" itself on click instead of waiting for the app to say so.

const IDLE = Object.freeze({ phase: 'idle' });
const target = new EventTarget();
let current = IDLE;

export const dumpProgress = {
  /** `{ phase: 'idle' }`, `{ phase: 'dumping' }` or `{ phase: 'analyzing', step, percent }`. */
  get: () => current,
  isIdle: () => current.phase === 'idle',
  set(state) {
    current = state;
    target.dispatchEvent(new Event('change'));
  },
  reset() {
    this.set(IDLE);
  },
  /** Calls `listener` on every change; returns the function that stops it. */
  subscribe(listener) {
    target.addEventListener('change', listener);
    return () => target.removeEventListener('change', listener);
  },
};
