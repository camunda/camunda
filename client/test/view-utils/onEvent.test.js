import {expect} from 'chai';
import sinon from 'sinon';
import {jsx} from 'view-utils';
import {mountTemplate, triggerEvent} from 'testHelpers';
import {OnEvent} from 'view-utils/onEvent';

describe('<OnEvent>', () => {
  let listener;
  let node;
  let update;

  beforeEach(() => {
    listener = sinon.spy();
  });

  describe('with single event', () => {
    beforeEach(() => {
      ({node, update} = mountTemplate(<button>
        <OnEvent event="click" listener={listener} />
      </button>));
    });

    it('should call listener with last state, event and node on event', () => {
      const state = 'st-1';
      const button = node.querySelector('button');

      update(state);
      triggerEvent({
        node: button,
        eventName: 'click'
      });

      const [{node: listenerNode, state: lastState, event}] = listener.firstCall.args;

      expect(listenerNode).to.equal(button);
      expect(lastState).to.eql(state);
      expect(event instanceof Event).to.eql(true, 'expected event to be instance of Event');
    });
  });

  describe('with multiple event', () => {
    const state = 'st-1';
    let events;
    let button;

    beforeEach(() => {
      events = ['click', 'mouseover'];

      ({node, update} = mountTemplate(<button>
        <OnEvent event={events} listener={listener} />
      </button>));

      update(state);

      button = node.querySelector('button');
    });

    it('should call listener with last state, event and node on first event', () => {
      triggerEvent({
        node: button,
        eventName: 'click'
      });

      const [{node: listenerNode, state: lastState, event}] = listener.firstCall.args;

      expect(listenerNode).to.equal(button);
      expect(lastState).to.eql(state);
      expect(event instanceof Event).to.eql(true, 'expected event to be instance of Event');
    });

    it('should call listener with last state, event and node on second event', () => {
      triggerEvent({
        node: button,
        eventName: 'mouseover'
      });

      const [{node: listenerNode, state: lastState, event}] = listener.firstCall.args;

      expect(listenerNode).to.equal(button);
      expect(lastState).to.eql(state);
      expect(event instanceof Event).to.eql(true, 'expected event to be instance of Event');
    });
  });
});
