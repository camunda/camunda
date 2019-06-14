/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

class CsrfToken {
  static instance = null;

  csrfToken = null;

  static getInstance() {
    if (CsrfToken.instance == null) {
      CsrfToken.instance = new CsrfToken();
    }

    return this.myInstance;
  }

  getToken() {
    return this.csrfToken;
  }

  setToken(token) {
    this.csrfToken = token;
  }
}
