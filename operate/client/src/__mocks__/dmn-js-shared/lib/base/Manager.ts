/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {View} from 'dmn-js-shared/lib/base/Manager';

const mockedModules: {[module: string]: unknown} = {
  canvas: {
    zoom: vi.fn(),
    resized: vi.fn(),
    addMarker: vi.fn(),
    removeMarker: vi.fn(),
  },
  overlays: {
    add: vi.fn(),
    remove: vi.fn(),
  },
};

class Manager {
  container: HTMLElement;
  constructor(
    {container}: {container: HTMLElement} = {
      container: document.createElement('div'),
    },
  ) {
    this.container = container;
  }
  destroy = vi.fn();
  getViews = vi.fn(() => [
    {id: 'invoiceClassification', type: 'decisionTable'},
    {id: 'calc-key-figures', type: 'literalExpression'},
  ]);
  open = vi.fn((view: View) => {
    if (view.type === 'decisionTable') {
      this.container.innerHTML = 'DecisionTable view mock';
    }
    if (view.type === 'literalExpression') {
      this.container.innerHTML = 'LiteralExpression view mock';
    }
  });
  importXML = vi.fn(() => {
    this.container.innerHTML = 'Default View mock';
    return Promise.resolve({});
  });
  getActiveViewer = () => ({
    get: (module: string) => mockedModules[module],
    on: vi.fn(),
    off: vi.fn(),
  });
  getDefinitions = vi.fn(() => {
    return {name: 'Definitions Name Mock', id: 'definitionId'};
  });
}

export default Manager;
