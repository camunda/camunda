import {expect} from 'chai';
import sinon from 'sinon';
import {createEventsBus, DESTROY_EVENT, ALL_EVENTS} from 'view-utils/events';

describe('EventsBus', () => {
  const eventName = 'event-name';
  let eventsBus;
  let listener;

  beforeEach(() => {
    eventsBus = createEventsBus();
    listener = sinon.spy();
  });

  describe('subscribe to father', () => {
    let childEventsBus;

    beforeEach(() => {
      childEventsBus = createEventsBus(eventsBus);
    });

    it('should fire listener when event is fired on father', () => {
      const data = {a: 1};

      childEventsBus.on(eventName, listener);

      eventsBus.fireEvent(eventName, data);

      expect(listener.calledWith({
        name: eventName,
        data,
        stopped: false
      })).to.eql(true);
    });
  });

  describe('subscribeToAll', () => {
    let clientEventsBus;

    beforeEach(() => {
      clientEventsBus = createEventsBus();
      clientEventsBus.subscribeToAll(eventsBus);
    });

    it('should call listener when event is fired on subscribed events bus', () => {
      const data = {a: 1};

      clientEventsBus.on(eventName, listener);
      eventsBus.fireEvent(eventName, data);

      expect(listener.calledWith({
        name: eventName,
        data,
        stopped: false
      })).to.eql(true);
    });

    it('should not call listener when subscribed events bus stops event', () => {
      eventsBus.on(eventName, event =>
        event.stopPropagation()
      );

      clientEventsBus.on(eventName, listener);
      eventsBus.fireEvent(eventName);

      expect(listener.called).not.to.eql(true);
    });

    it('should remove subscription when destroy event is received', () => {
      clientEventsBus.on(eventName, listener);
      clientEventsBus.fireEvent(DESTROY_EVENT);
      eventsBus.fireEvent(eventName);

      expect(listener.called).not.to.eql(true);
    });
  });

  describe('on', () => {
    it('should call listener when subscribed event is fired', () => {
      const data = {a: 1};

      eventsBus.on(eventName, listener);
      eventsBus.fireEvent(eventName, data);

      expect(listener.calledWith({
        name: eventName,
        data,
        stopped: false
      })).to.eql(true);
    });

    it('should always call listener when subscribed to all events', () => {
      const data = {a: 1};

      eventsBus.on(ALL_EVENTS, listener);
      eventsBus.fireEvent(eventName, data);

      expect(listener.calledWith({
        name: eventName,
        data,
        stopped: false
      })).to.eql(true);
    });

    it('should not call listener when subscription to event is removed', () => {
      const data = {a: 1};
      const unsubscribe = eventsBus.on(eventName, listener);

      unsubscribe();
      eventsBus.fireEvent(eventName, data);

      expect(listener.called).to.eql(false);
    });
  });
});
