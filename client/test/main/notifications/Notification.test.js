import {expect} from 'chai';
import {jsx} from 'view-utils';
import sinon from 'sinon';
import {mountTemplate, triggerEvent} from 'testHelpers';
import {Notification, __set__, __ResetDependency__} from 'main/notifications/Notification';

describe('<Notification>', () => {
  let removeNotification;
  let state;
  let node;
  let update;

  beforeEach(() => {
    removeNotification = sinon.spy();
    __set__('removeNotification', removeNotification);

    state = {
      status: 'some status',
      text: 'help me!'
    };

    ({node, update} = mountTemplate(<Notification />));

    update(state);
  });

  afterEach(() => {
    __ResetDependency__('removeNotification');
  });

  it('should display status and text', () => {
    expect(node).to.contain.text(state.status);
    expect(node).to.contain.text(state.text);
  });

  it('should have close button that calls removeNotification on click', () => {
    const button = node.querySelector('button');

    expect(button).to.contain.text('×');
    expect(button).to.have.attribute('aria-label', 'close');
    expect(button).to.have.attribute('type', 'button');

    triggerEvent({
      node: button,
      eventName: 'click'
    });

    expect(removeNotification.calledOnce)
      .to.eql(true, 'expected removeNotification to be called once');
    expect(removeNotification.calledWith(state))
      .to.eql(true, 'expected removeNotification to be called with state');
  });

  it('should create notification with alert-info class', function() {
    expect(node.querySelector('.alert-info')).to.exist;
  });

  it('should create notification with alert-error class', function() {
    update({
      ...state,
      isError: true
    });

    expect(node.querySelector('.alert-error')).to.exist;
  });
});
