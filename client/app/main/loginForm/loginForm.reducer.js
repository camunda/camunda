export const LOGIN_ERROR_ACTION = 'LOGIN_ERROR_ACTION';
export const LOGIN_IN_PROGRESS = 'LOGIN_IN_PROGRESS';

export function reducer(state = {error: false, inProgress: false}, action) {
  if (action.type === LOGIN_ERROR_ACTION) {
    const {error} = action;

    return {
      ...state,
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
