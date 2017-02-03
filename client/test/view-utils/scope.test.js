import {expect} from 'chai';
import {jsx, Scope} from 'view-utils';
import sinon from 'sinon';
import {mountTemplate, createMockComponent, mockFunction, DESTROY_EVENT} from 'testHelpers';

describe('<Scope>', () => {
  let Child;
  let state;
  let selector;
  let node;
  let update;
  let eventsBus;

  beforeEach(() => {
    Child = createMockComponent('child');

    state = {
      prop: 12
    };
  });

  describe('with function selector', () => {
    beforeEach(() => {
      selector = mockFunction(({prop}) => {
        return prop;
      });

      ({node, update, eventsBus} = mountTemplate(<Scope selector={selector}>
        <Child />
      </Scope>));

      update(state);
    });

    it('should process state with selector function', () => {
      expect(selector.calledWith(state)).to.eql(true);
    });

    it('should pass new state to Child update', () => {
      expect(Child.mocks.update.calledWith(state.prop)).to.eql(true);
    });

    it('should display child', () => {
      expect(node).to.contain.text('child');
    });

    it('should pass destroy event to child', () => {
      const childEventsBus = Child.getEventsBus(0);
      const listener = sinon.spy();

      childEventsBus.on(DESTROY_EVENT, listener);

      eventsBus.fireEvent(DESTROY_EVENT);

      expect(listener.calledOnce).to.eql(true);
    });
  });

  describe('with text selector', () => {
    beforeEach(() => {
      ({node, update, eventsBus} = mountTemplate(<Scope selector="prop">
        <Child />
      </Scope>));

      update(state);
    });

    it('should pass new state to Child update', () => {
      expect(Child.mocks.update.calledWith(state.prop)).to.eql(true);
    });

    it('should display child', () => {
      expect(node).to.contain.text('child');
    });

    it('should pass destroy event to child', () => {
      const childEventsBus = Child.getEventsBus(0);
      const listener = sinon.spy();

      childEventsBus.on(DESTROY_EVENT, listener);

      eventsBus.fireEvent(DESTROY_EVENT);

      expect(listener.calledOnce).to.eql(true);
    });
  });
});
