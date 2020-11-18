/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {request} from './request';

export function get(url: any, query?: any, options = {}) {
  return request({
    url,
    query,
    method: 'GET',
    ...options,
  });
}

export function post(url: any, body: any, options = {}) {
  return request({
    url,
    body,
    method: 'POST',
    ...options,
  });
}

export function put(url: any, body: any, options = {}) {
  return request({
    url,
    body,
    method: 'PUT',
    ...options,
  });
}

export function del(url: any, query: any, options = {}) {
  return request({
    url,
    query,
    method: 'DELETE',
    ...options,
  });
}
