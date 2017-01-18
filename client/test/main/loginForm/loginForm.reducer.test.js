import {expect} from 'chai';
import {reducer, createChangeLoginUserAction, createChangeLoginPasswordAction,
  createLoginErrorAction, createLoginInProgressAction} from 'main/loginForm/loginForm.reducer';

describe('loginForm reducer', () => {
  let inputState;

  beforeEach(() => {
    inputState = {
      extra: 'd',
      user: 'user1',
      password: 'password1',
      error: false,
      inProgress: false
    };
  });

  it('should not change input state on any action', () => {
    reducer(inputState, {
      type: 'blah'
    });
    reducer(inputState, createLoginErrorAction(true));
    reducer(inputState, createLoginErrorAction(false));
    reducer(inputState, createChangeLoginPasswordAction('new_pass'));
    reducer(inputState, createChangeLoginUserAction('new_user'));

    expect(inputState).to.eql({
      extra: 'd',
      user: 'user1',
      password: 'password1',
      error: false,
      inProgress: false
    });
  });

  it('should change user on change user action', () => {
    const user = 'new_user1';
    const outputState = reducer(inputState, createChangeLoginUserAction(user));

    expect(outputState).to.eql({
      ...inputState,
      user
    });
  });

  it('should change password on password change action', () => {
    const password = 'new_password1';
    const outputState = reducer(inputState, createChangeLoginPasswordAction(password));

    expect(outputState).to.eql({
      ...inputState,
      password
    });
  });

  it('should change error and clean user and password on change error action', () => {
    const out1 = reducer(inputState, createLoginErrorAction(true));
    const out2 = reducer(out1, createLoginErrorAction(false));

    expect(out1).to.eql({
      ...inputState,
      user: '',
      password: '',
      error: true,
      inProgress: false
    }, 'expected error to be true');
    expect(out2).to.eql({
      ...inputState,
      user: '',
      password: '',
      error: false,
      inProgress: false
    }, 'expected error to be false');
  });

  it('should set inProgress property of state to true', () => {
    const outputState = reducer(inputState, createLoginInProgressAction());

    expect(outputState.inProgress).to.eql(true);
  });
});
