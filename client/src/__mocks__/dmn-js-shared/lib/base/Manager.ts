/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

class Manager {
  container: any;
  constructor({container}: any = {}) {
    this.container = container;
  }
  destroy = jest.fn();
  getViews = jest.fn(() => []);
  open = jest.fn(() => {
    this.container.innerHTML = 'Decision View mock';
  });
  importXML = jest.fn(() => {
    return Promise.resolve({});
  });
}

export default Manager;
