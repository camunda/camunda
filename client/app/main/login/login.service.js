import {$window, dispatchAction} from 'view-utils';
import {createLoginAction, createClearLoginAction} from './login.reducer';
import {authenticate, checkToken} from './loginBackend.mock';

const sessionStorage = $window.sessionStorage;
const LOGIN_KEY = 'LOGIN_KEY';

export function clearLogin() {
  sessionStorage.removeItem(LOGIN_KEY);

  dispatchAction(createClearLoginAction());
}

function getLogin() {
  const loginContent = sessionStorage.getItem(LOGIN_KEY);

  if (!loginContent) {
    return null;
  }

  return JSON.parse(loginContent);
}

function saveLogin(user, token) {
  sessionStorage.setItem(LOGIN_KEY,
    JSON.stringify({
      user,
      token
    })
  );
}

export function refreshAuthentication() {
  const {user, token} = getLogin() || {};

  return checkToken(token)
    .then(() => {
      dispatchAction(
        createLoginAction(user, token)
      );
    })
    .catch(() => {
      clearLogin();
    });
}

export function login(user, password) {
  return authenticate(user, password)
    .then(token => {
      saveLogin(user, token);
      dispatchAction(
        createLoginAction(user, token)
      );
    });
}
