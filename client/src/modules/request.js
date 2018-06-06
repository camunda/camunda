let responseInterceptor = null;

const BASE_URL =
  process.env.NODE_ENV === 'development' ? 'http://localhost:8080' : '';

export function get(url, query, options = {}) {
  return request({
    url,
    query,
    method: 'GET',
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

export function put(url, body, options = {}) {
  return request({
    url,
    body,
    method: 'PUT',
    ...options
  });
}

export function del(url, query, options = {}) {
  return request({
    url,
    query,
    method: 'DELETE',
    ...options
  });
}

export function addResponseInterceptor(fct) {
  responseInterceptor = fct;
}

export function removeResponseInterceptor(fct) {
  responseInterceptor = null;
}

export async function request({url, method, body, query, headers}) {
  const resourceUrl = query
    ? `${BASE_URL}${url}?${stringifyQuery(query)}`
    : `${BASE_URL}${url}`;

  let response = await fetch(resourceUrl, {
    method,
    credentials: 'include',
    body: typeof body === 'string' ? body : JSON.stringify(body),
    headers: {
      'Content-Type': 'application/json',
      ...headers
    },
    mode: 'cors'
  });

  if (typeof responseInterceptor === 'function') {
    await responseInterceptor(response);
  }

  if (response.status >= 200 && response.status < 300) {
    return response;
  } else {
    throw response;
  }
}

export function stringifyQuery(query) {
  return Object.keys(query).reduce((queryStr, key) => {
    const value = query[key];

    if (queryStr === '') {
      return `${key}=${encodeURIComponent(value)}`;
    }

    return `${queryStr}&${key}=${encodeURIComponent(value)}`;
  }, '');
}
