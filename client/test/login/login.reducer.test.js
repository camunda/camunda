import {expect} from 'chai';
import {reducer, createLoginAction, createClearLoginAction, createLoginCheckAction} from 'login/login.reducer';

describe('login.reducer', () => {
  const user = 'user-1';
  const token = 'token-1';

  it('should produce null state be default', () => {
    expect(
      reducer(undefined, {type: 'ot'})
    ).to.eql(null);
  });

  it('should set login from action', () => {
    expect(
      reducer(undefined, createLoginAction(user, token))
    ).to.eql({
      user,
      token
    });
  });

  it('should clear login on clear login action', () => {
    const originalState = {user, token};

    expect(
      reducer(originalState, createClearLoginAction())
    ).to.eql(null);
  });

  it('should set login to being checked', () => {
    expect(
      reducer(undefined, createLoginCheckAction())
    ).to.eql({
      check: true
    });
  });
});
