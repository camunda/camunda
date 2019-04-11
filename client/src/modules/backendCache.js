/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const cache = {};

export function fromCache({url, query = {}}) {
  if (endpoints[url]) {
    const cached = findEntry(cache[url], query);
    return cached && cached.clone();
  }
}

export function toCache({url, query = {}}, response) {
  if (endpoints[url]) {
    cache[url] = cache[url] || [];

    const entry = {props: {}, response: response.clone()};
    endpoints[url].forEach(prop => (entry.props[prop] = query[prop]));

    cache[url].push(entry);
  }
}

function findEntry(entries = [], query) {
  const entry = entries.find(({props}) =>
    Object.keys(props).every(prop => props[prop] === query[prop])
  );
  return entry && entry.response;
}

const endpoints = {
  'api/meta/version': [],
  'api/camunda': [],
  'api/alert/email/isEnabled': [],
  'api/share/isEnabled': [],
  'api/decision-definition/xml': ['key', 'version'],
  'api/process-definition/xml': ['processDefinitionKey', 'processDefinitionVersion'],
  'api/flow-node/flowNodeNames': ['processDefinitionKey', 'processDefinitionVersion']
};
