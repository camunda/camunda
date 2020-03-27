/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {diObject} from 'modules/testUtils';

export const mockedModules = {
  canvas: {
    zoom: jest.fn(),
    addMarker: jest.fn(),
    removeMarker: jest.fn(),
    resized: jest.fn(),
  },
  zoomScroll: {stepZoom: jest.fn()},

  elementRegistry: {
    getGraphics: jest.fn(() => ({
      querySelector: jest.fn(() => ({setAttribute: jest.fn()})),
      getBoundingClientRect: jest.fn(() => ({
        x: 0,
        y: 0,
        height: 0,
        width: 0,
      })),
      getBBox: jest.fn(() => ({x: 0, y: 0, height: 0, width: 0})),
    })),
    get: jest.fn((id) => ({businessObject: {name: id, di: diObject}})),
    forEach: jest.fn(() => {}),
  },
  graphicsFactory: {update: jest.fn(() => {})},
  eventBus: {on: jest.fn()},
  overlays: {add: jest.fn(), remove: jest.fn()},
};

export const mockedImportDefinitions = jest.fn((_, callback) => {
  callback();
});

class Viewer {
  constructor({container, bpmnRenderer} = {}) {
    this.container = container;
    this.bpmnRenderer = bpmnRenderer;
  }

  importDefinitions = mockedImportDefinitions;

  detach = jest.fn();

  get = (key) => mockedModules[key];
}

export default Viewer;
