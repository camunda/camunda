import {expect} from 'chai';
import {
  reducer,
  createAddNotificationAction,
  createRemoveNotificationAction
} from 'notifications/reducer';

describe('notifications reducer', () => {
  let state;

  beforeEach(() => {
    state = [
      {id: 'not1'},
      {id: 'not2'}
    ];
  });

  it('should add notification on add notification action', () => {
    const action = createAddNotificationAction('not3');
    const newState = reducer(state, action);

    expect(newState).to.eql([
      {id: 'not1'},
      {id: 'not2'},
      action.notification
    ]);
  });

  it('should remove notification on add notification action', () => {
    const newState = reducer(state, createRemoveNotificationAction({id: 'not1'}));

    expect(newState).to.eql([{id: 'not2'}]);
  });

  it('should create empty notification array as default state', () => {
    const newState = reducer(undefined, {type: '@@LOAD'});

    expect(newState).to.eql([]);
  });
});
