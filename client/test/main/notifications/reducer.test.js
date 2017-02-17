import {expect} from 'chai';
import {
  reducer,
  createAddNotificationAction,
  createRemoveNotificationAction
} from 'main/notifications/reducer';

describe('notifications reducer', () => {
  let state;

  beforeEach(() => {
    state = ['not1', 'not2'];
  });

  it('should add notification on add notification action', () => {
    const newState = reducer(state, createAddNotificationAction('not3'));

    expect(newState).to.eql(['not1', 'not2', 'not3']);
  });

  it('should remove notification on add notification action', () => {
    const newState = reducer(state, createRemoveNotificationAction('not1'));

    expect(newState).to.eql(['not2']);
  });

  it('should create empty notification array as default state', () => {
    const newState = reducer(undefined, {type: '@@LOAD'});

    expect(newState).to.eql([]);
  });
});
