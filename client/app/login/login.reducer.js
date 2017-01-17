export const LOGIN_ACTION = 'LOGIN_ACTION';
export const LOGIN_CHECK = 'LOGIN_CHECK';

export function reducer(state = null, action) {
  if (action.type === LOGIN_ACTION) {
    return action.login;
  }

  if (action.type === LOGIN_CHECK) {
    return {
      check: true
    };
  }

  return state;
}

export function createLoginAction(user, token) {
  return {
    type: LOGIN_ACTION,
    login: {
      user,
      token
    }
  };
}

export function createClearLoginAction() {
  return {
    type: LOGIN_ACTION,
    login: null
  };
}

export function createLoginCheckAction() {
  return {
    type: LOGIN_CHECK
  };
}
