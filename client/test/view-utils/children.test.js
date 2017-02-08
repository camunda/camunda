import {expect} from 'chai';
import {jsx} from 'view-utils';
import sinon from 'sinon';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {Children} from 'view-utils';

describe('<Children>', () => {
  let Child;
  let node;
  let eventsBus;
  let update;

  beforeEach(() => {
    Child = createMockComponent('Child');

    const children = [
      <Child />
    ];

    ({node, update, eventsBus} = mountTemplate(<Children children={children} />));
  });

  it('should update child component when update is called', () => {
    const state = {a: 1};

    update(state);

    expect(Child.mocks.update.calledWith(state)).to.eql(true);
  });

  it('should pass node to child template', () => {
    expect(Child.mocks.template.calledWith(node)).to.eql(true);
  });

  it('should fire event on child when parent gets event', () => {
    const eventName = 'some-event';
    const data = {a: 1};
    const eventListener = sinon.spy();

    const [, childEventsBus] = Child.mocks.template.calls[0];

    childEventsBus.on(eventName, eventListener);

    eventsBus.fireEvent(eventName, data);

    expect(eventListener.calledOnce).to.eql(true, 'expected event listener to be called once');

    const [event] = eventListener.firstCall.args;

    expect(event.name).to.eql(eventName);
    expect(event.data).to.equal(data);
  });
});
