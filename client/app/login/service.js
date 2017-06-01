import {$window, dispatchAction} from 'view-utils';
import {createLoginAction, createClearLoginAction, createLoginCheckAction} from './reducer';
import {get, post} from 'http';

const localStorage = $window.localStorage;
const LOGIN_KEY = 'LOGIN_KEY';

export function clearLoginFromSession() {
  localStorage.removeItem(LOGIN_KEY);

  dispatchAction(createClearLoginAction());
}

export function clearLogin() {
  return get('/api/authentication/logout').then(clearLoginFromSession);
}

function getLogin() {
  const loginContent = localStorage.getItem(LOGIN_KEY);

  if (!loginContent) {
    return null;
  }

  return JSON.parse(loginContent);
}

function saveLogin(user, token) {
  localStorage.setItem(LOGIN_KEY,
    JSON.stringify({
      user,
      token
    })
  );
}

function checkToken(token) {
  const headers = !token ? {} : {
    'X-Optimize-Authorization' : `Bearer ${token}`
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
