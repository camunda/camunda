import {expect} from 'chai';
import sinon from 'sinon';
import {
  addNotification,
  removeNotification,
  __set__,
  __ResetDependency__
} from 'notifications/service';

describe('notifications service', () => {
  let dispatchAction;
  let $window;
  let createAddNotificationAction;
  let createRemoveNotificationAction;

  beforeEach(() => {
    dispatchAction = sinon.spy();
    $window = {
      setTimeout: sinon.spy()
    };

    createAddNotificationAction = sinon.stub();
    createAddNotificationAction.returns(createAddNotificationAction);

    createRemoveNotificationAction = sinon.stub();
    createRemoveNotificationAction.returns(createRemoveNotificationAction);

    __set__('dispatchAction', dispatchAction);
    __set__('$window', $window);
    __set__('createAddNotificationAction', createAddNotificationAction);
    __set__('createRemoveNotificationAction', createRemoveNotificationAction);
  });

  afterEach(() => {
    __ResetDependency__('dispatchAction');
    __ResetDependency__('$window');
    __ResetDependency__('createAddNotificationAction');
    __ResetDependency__('createRemoveNotificationAction');
  });

  describe('addNotification', () => {
    const status = 'st-1';
    const text = 't-1';
    const isError = true;
    const timeout = 1400;
    let remove;
    let removalId;

    beforeEach(() => {
      remove = addNotification({status, text, isError, timeout});
      ({id: removalId} = createAddNotificationAction.lastCall.args[0]);
    });

    it('should dispatch add notification action', () => {
      const {id, ...actualNotification} = createAddNotificationAction.lastCall.args[0];

      expect(actualNotification).to.eql({status, text, isError});
      expect(typeof id).to.eql('number');
      expect(dispatchAction.calledWith(createAddNotificationAction))
        .to.eql(true, 'expected add notification action to be dispatched');
    });

    it('should return function that removes this notification', () => {
      remove();

      const {id, ...actualNotification} = createRemoveNotificationAction.lastCall.args[0];

      expect(actualNotification).to.eql({status, text, isError});
      expect(id).to.eql(removalId);
      expect(dispatchAction.calledWith(createRemoveNotificationAction))
        .to.eql(true, 'expected remove notification action to be dispatched');
    });

    it('should start timeout with remove function and given timeout', () => {
      expect($window.setTimeout.calledWith(remove, timeout)).to.eql(true);
    });

    it('should skip timout if it is 0', () => {
      $window.setTimeout.reset();

      addNotification({status, text, isError, timeout: 0});

      expect($window.setTimeout.called).to.eql(false);
    });
  });

  describe('removeNotification', () => {
    const notification = 'not';

    it('should dispatch remove notification action', () => {
      removeNotification(notification);

      expect(createRemoveNotificationAction.calledWith(notification))
        .to.eql(true, 'expected remove notification action to be created');
      expect(dispatchAction.calledWith(createRemoveNotificationAction))
        .to.eql(true, 'expected remove notification action to be dispatched');
    });
  });
});
