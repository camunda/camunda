export const ALL_EVENTS = '$all';
export const DESTROY_EVENT = '$destroy';

export function createEventsBus(father) {
  return new Events(father);
}

class Events {
  constructor(father) {
    this._listeners = {};
    this._subscriptions = [];

    this.subscribeToAll(father);
  }

  subscribeToAll(events) {
    if (events) {
      const subscription = events.on(ALL_EVENTS, (event) => {
        if (event.stopped && event.name !== DESTROY_EVENT) {
          return;
        }

        this.fireEvent(event.name, event.data);
      });

      this._subscriptions.push(subscription);
    }
  }

  on(name, listener) {
    this._addListenerForName(name, listener);

    return this._removeListenerForName.bind(this, name, listener);
  }

  fireEvent(name, data) {
    const event = new Event({name, data});

    this._fireListeners(name, event);
    this._fireListeners(ALL_EVENTS, event);

    this._handleDestroyEvent(name);
  }

  _fireListeners(name, event) {
    const listeners = this._getListenersForName(name);
    const len = listeners.length;

    for (let i = 0; i < len; i++) {
      listeners[i](event);
    }
  }

  _handleDestroyEvent(name) {
    if (name === DESTROY_EVENT) {
      return this._removeAllSubscriptions();
    }
  }

  _removeAllSubscriptions() {
    this._listeners = {};
    this._subscriptions.forEach(subscription => subscription());
    this._subscriptions = [];
  }

  _addListenerForName(name, listener) {
    const listeners = this._getListenersForName(name);

    listeners.push(listener);

    return this._removeListenerForName.bind(this, name, listener);
  }

  _removeListenerForName(name, listener) {
    this._listeners[name] = this._getListenersForName(name)
      .filter(otherListener => listener !== otherListener);
  }

  _getListenersForName(name) {
    if (!this._listeners[name]) {
      this._listeners[name] = [];
    }

    return this._listeners[name];
  }
}

class Event {
  constructor({name, data, stopped = false}) {
    this.name = name;
    this.data = data;
    this.stopped = stopped;
  }

  stopPropagation() {
    this.stopped = true;
  }
}
