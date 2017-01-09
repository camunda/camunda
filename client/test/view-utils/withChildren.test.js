import {expect} from 'chai';
import sinon from 'sinon';
import {jsx, withChildren, DESTROY_EVENT} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';

describe('withChildren', () => {
  let Parent;
  let Child;
  let node;
  let update;
  let eventsBus;
  let state;

  beforeEach(() => {
    Parent = createMockComponent('parent');
    Child = createMockComponent('child');

    const ParentWithChildren = withChildren(Parent);

    ({node, update, eventsBus} =  mountTemplate(<ParentWithChildren>
      text1
      <Child/>
    </ParentWithChildren>));

    state = 'some-state';

    update(state);
  });

  it('should update Parent with state', () => {
    expect(Parent.mocks.update.calledWith(state)).to.eql(true);
  });

  it('should update Child with state', () => {
    expect(Child.mocks.update.calledWith(state)).to.eql(true);
  });

  it('should contain Parent text', () => {
    expect(node).to.contain.text('parent');
  });

  it('should contain Child text', () => {
    expect(node).to.contain.text('child');
  });

  it('should pass destroy event to Parent', () => {
    const parentEventsBus = Parent.getEventsBus(0);
    const listener = sinon.spy();

    parentEventsBus.on(DESTROY_EVENT, listener);

    eventsBus.fireEvent(DESTROY_EVENT);

    expect(listener.calledOnce).to.eql(true);
  });
});
