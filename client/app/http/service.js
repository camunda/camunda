import {getLogin, clearLoginFromSession} from 'login';
import {getRouter, getLastRoute} from 'router';

const router = getRouter();

const $fetch = fetch; //for mocking ;)
const DEFAULT_HEADERS = {
  'Content-Type': 'application/json'
};

window.get = get;
window.post = post;

export function put(url, body, options = {}) {
  return request({
    url,
    body,
    method: 'PUT',
    ...options
  });
}

export function post(url, body, options = {}) {
  return request({
    url,
    body,
    method: 'POST',
    ...options
  });
}

export function get(url, query, options = {}) {
  return request({
    url,
    query,
    method: 'GET',
    ...options
  });
}

export function request({url, method, body, query, headers}) {
  const resourceUrl = query ? `${url}?${formatQuery(query)}` : url;

  return $fetch(resourceUrl, {
    method,
    body: processBody(body),
    headers: {
      ...DEFAULT_HEADERS,
      ...createAuthorizationHeader(),
      ...headers
    },
    mode: 'cors'
  })
  .then(response => {
    const {status} = response;

    if (status >= 200 && status < 300) {
      return response;
    } else if (status === 401) {
      const {name, params} = getLastRoute();

      if (name !== 'login') {
        clearLoginFromSession();
        router.goTo('login', {name, params: JSON.stringify(params)}, true);
      }
    }

    return Promise.reject(response);
  });
}

function createAuthorizationHeader() {
  const login = getLogin();

  if (login && login.token) {
    return {
      'X-Optimize-Authorization' : `Bearer ${login.token}`
    };
  }

  return {};
}

export function formatQuery(query) {
  return Object
    .keys(query)
    .reduce((queryStr, key) => {
      const value = query[key];

      if (queryStr === '') {
        return `${key}=${encodeURIComponent(value)}`;
      }

      return `${queryStr}&${key}=${encodeURIComponent(value)}`;
    }, '');
}

function processBody(body) {
  if (typeof body === 'string') {
    return body;
  }

  return JSON.stringify(body);
}
