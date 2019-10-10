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

  update(name, apiCall, params) {
    if (!params) {
      // Required when single DM 'get' methods are called without the params required to do the api request.
      //In this case the last used once are used again.
      return this.getEndpointsbyNames([name]).params;
    } else {
      this.set(name, {
        params,
        apiCall
      });
      return params;
    }
  }

  getEndpointsbyNames(names = []) {
    return names.reduce((acc, name) => {
      return {...acc, [name]: this.cache[name]};
    }, {});
  }
}
