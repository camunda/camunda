/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export default class Csrf {
  static instance = null;

  static getInstance() {
    if (Csrf.instance == null) {
      Csrf.instance = new Csrf();
    }

    return this.instance;
  }

  token = null;

  getToken() {
    return this.token;
  }

  setToken(token) {
    this.token = token;
  }
}
