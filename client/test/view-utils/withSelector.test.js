import {expect} from 'chai';
import sinon from 'sinon';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {withSelector, jsx} from 'view-utils';

describe('withSelector', () => {
  const other = 'prop';
  const state = 's-state!';
  let Child;
  let selector;
  let node;
  let update;
  let eventsBus;

  beforeEach(() => {
    Child = createMockComponent('child');

    selector = sinon.stub().returns(state);
    const ChildWithSelector = withSelector(Child);

    ({node, update, eventsBus} = mountTemplate(
      <ChildWithSelector selector={selector} other={other}/>
    ));
  });

  it('should pass other property to Child', () => {
    expect(Child.calledWith({
      children: [],
      other
    })).to.eql(true);
  });

  it('should display Child', () => {
    expect(node).to.contain.text(Child.text);
  });

  it('should call selector on update', () => {
    const state = 'other-state';

    update(state);

    expect(selector.calledWith(state)).to.eql(true);
  });

  it('should call Child update with new state on update', () => {
    update();

    expect(Child.mocks.update.calledWith(state)).to.eql(true);
  });

  it('should pass events to Child', () => {
    const data = 'data-94';
    const event = 'ev-1';
    const listener = sinon.spy();
    const childEventsBus = Child.getEventsBus(0);

    childEventsBus.on(event, listener);

    eventsBus.fireEvent(event, data);

    expect(listener.calledWith({
      name: event,
      data,
      stopped: false
    })).to.eql(true);
  });

  describe('with custom selector property name', () => {
    beforeEach(() => {
      selector = sinon.stub().returns(state);
      const ChildWithSelector = withSelector(Child, 'selector2');

      ({node, update, eventsBus} = mountTemplate(
        <ChildWithSelector selector2={selector} other={other}/>
      ));
    });

    it('should pass other property to Child', () => {
      expect(Child.calledWith({
        children: [],
        other
      })).to.eql(true);
    });
  });
});
