import {expect} from 'chai';
import {reducer, createLoginAction, createClearLoginAction} from 'login/login.reducer';

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
      login: {
        user,
        token
      }
    });
  });

  it('should not change other properties of state', () => {
    const originalState = {a: 1};

    expect(
      reducer(originalState, createLoginAction(user, token))
    ).to.eql({
      login: {
        user,
        token
      },
      a: 1
    });
  });

  it('should clear login on clear login action', () => {
    const originalState = {
      login: {user, token},
      a: 1
    };

    expect(
      reducer(originalState, createClearLoginAction())
    ).to.eql({
      login: null,
      a: 1
    });
  });
});
