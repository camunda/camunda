let responseInterceptor = null;

export const BASE_URL =
  process.env.NODE_ENV === 'development' ? 'http://localhost:8080' : '';

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

export function setResponseInterceptor(fct) {
  responseInterceptor = fct;
}

export function resetResponseInterceptor() {
  responseInterceptor = null;
}
