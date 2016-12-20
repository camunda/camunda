export const LOGIN_ACTION = 'LOGIN_ACTION';

export function reducer(state = null, action) {
  if (action.type === LOGIN_ACTION) {
    return action.login;
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
