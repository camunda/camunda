/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export default class RequestCache {
  constructor() {
    this.cache = {};
  }

  set(name, request = {}) {
    const {apiCall, params} = request;
    this.cache = {...this.cache, [name]: {params, apiCall}};
  }

  getEndpointNames() {
    return Object.keys(this.cache);
  }

  getEndpointsbyNames(names = []) {
    return names.reduce((acc, name) => {
      return {...acc, [name]: this.cache[name]};
    }, {});
  }
}
