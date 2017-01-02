const $XMLHttpRequest = XMLHttpRequest; //for mocking ;)
const DEFAULT_HEADERS = {
  'Content-Type': 'application/json'
};

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
  return new Promise((resolve, reject) => {
    const http = new $XMLHttpRequest();
    const resourceUrl = query ? `${url}?${formatQuery(query)}` : url;

    http.onreadystatechange = () => {
      if (http.readyState === $XMLHttpRequest.DONE) {
        const response = tryToParseJson(http.responseText);

        if (http.status >= 200 && http.status < 300) {
          resolve(response);
        } else {
          reject({
            status: http.status,
            response
          });
        }
      }
    };

    http.open(method, resourceUrl, true);
    applyHeaders(http, headers);
    http.send(
      processBody(body)
    );
  });
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

function tryToParseJson(response) {
  try {
    return JSON.parse(response);
  } catch (err) {
    return response;
  }
}

function processBody(body) {
  if (typeof body === 'string') {
    return body;
  }

  return JSON.stringify(body);
}

function applyHeaders(http, headers) {
  const appliedHeaders = {
    ...DEFAULT_HEADERS,
    ...headers
  };

  Object
    .keys(appliedHeaders)
    .forEach(header => {
      const value = appliedHeaders[header];

      http.setRequestHeader(header, value);
    });
}
