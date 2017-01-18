export const CHANGE_LOGIN_USER = 'CHANGE_LOGIN_USER';
export const CHANGE_LOGIN_PASSWORD = 'CHANGE_LOGIN_PASSWORD';
export const LOGIN_ERROR_ACTION = 'LOGIN_ERROR_ACTION';
export const LOGIN_IN_PROGRESS = 'LOGIN_IN_PROGRESS';

export function reducer(state = {user: '', password: '', error: false, inProgress: false}, action) {
  if (action.type === CHANGE_LOGIN_USER) {
    const {user} = action;

    return {
      ...state,
      user
    };
  }

  if (action.type === CHANGE_LOGIN_PASSWORD) {
    const {password} = action;

    return {
      ...state,
      password
    };
  }

  if (action.type === LOGIN_ERROR_ACTION) {
    const {error} = action;

    return {
      ...state,
      user: '',
      password: '',
      inProgress: false,
      error
    };
  }

  if (action.type === LOGIN_IN_PROGRESS) {
    return {
      ...state,
      inProgress: true
    };
  }

  return state;
}

export function createChangeLoginUserAction(user) {
  return {
    type: CHANGE_LOGIN_USER,
    user
  };
}

export function createChangeLoginPasswordAction(password) {
  return {
    type: CHANGE_LOGIN_PASSWORD,
    password
  };
}

export function createLoginErrorAction(error = true) {
  return {
    type: LOGIN_ERROR_ACTION,
    error
  };
}

export function createLoginInProgressAction() {
  return {
    type: LOGIN_IN_PROGRESS
  };
}
