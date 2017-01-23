import {$window, dispatchAction} from 'view-utils';
import {createLoginAction, createClearLoginAction, createLoginCheckAction} from './login.reducer';
import {get, post} from 'http';

const sessionStorage = $window.sessionStorage;
const LOGIN_KEY = 'LOGIN_KEY';

function clearLoginFromSession() {
  sessionStorage.removeItem(LOGIN_KEY);

  dispatchAction(createClearLoginAction());
}

export function clearLogin() {
  return get('/api/authentication/logout').then(clearLoginFromSession);
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

function checkToken(token) {
  const headers = !token ? {} : {
    Authorization: `Bearer ${token}`
  };

  return get('/api/authentication/test', null, {
    headers
  });
}

export function refreshAuthentication() {
  const {user, token} = getLogin() || {};

  dispatchAction(createLoginCheckAction());

  return checkToken(token)
    .then(() => {
      dispatchAction(
        createLoginAction(user, token)
      );
    })
    .catch(() => {
      clearLoginFromSession();
    });
}

export function login(user, password) {
  return post('/api/authentication', {
    username: user,
    password
  })
  .then(response => response.text())
  .then(token => {
    saveLogin(user, token);
    dispatchAction(
      createLoginAction(user, token)
    );
  });
}
