/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const handlers = [];

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

export function del(url, query, options = {}) {
  return request({
    url,
    query,
    method: 'DELETE',
    ...options
  });
}

export function addHandler(fct, priority = 0) {
  handlers.push({fct, priority});
  handlers.sort((a, b) => b.priority - a.priority);
}

export function removeHandler(fct) {
  handlers.splice(handlers.indexOf(handlers.find(entry => entry.fct === fct)), 1);
}

export async function request(payload) {
  const {url, method, body, query, headers} = payload;
  const resourceUrl = query ? `${url}?${formatQuery(query)}` : url;

  let response = await fetch(resourceUrl, {
    method,
    body: processBody(body),
    headers: {
      'Content-Type': 'application/json',
      ...headers
    },
    mode: 'cors',
    credentials: 'same-origin'
  });

  for (let i = 0; i < handlers.length; i++) {
    response = await handlers[i].fct(response, payload);
  }

  if (response.status >= 200 && response.status < 300) {
    return response;
  } else {
    throw response;
  }
}

export function formatQuery(query) {
  return Object.keys(query).reduce((queryStr, key) => {
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
