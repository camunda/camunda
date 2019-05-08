/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

class Viewer {
  constructor({container, bpmnRenderer} = {}) {
    this.canvas = {
      zoom: jest.fn(),
      addMarker: jest.fn(),
      removeMarker: jest.fn(),
      resized: jest.fn()
    };
    this.zoomScroll = {stepZoom: jest.fn()};
    this.container = container;
    this.bpmnRenderer = bpmnRenderer;
    this.elementRegistry = {
      getGraphics: jest.fn(() => ({
        querySelector: jest.fn(() => ({setAttribute: jest.fn()})),
        getBoundingClientRect: jest.fn(() => ({
          x: 0,
          y: 0,
          height: 0,
          width: 0
        })),
        getBBox: jest.fn(() => ({x: 0, y: 0, height: 0, width: 0}))
      })),
      get: jest.fn(id => ({businessObject: {name: id}}))
    };
    this.eventBus = {on: jest.fn()};
    this.overlays = {add: jest.fn(), remove: jest.fn()};
  }

  importDefinitions = jest.fn((_, callback) => {
    callback();
  });

  detach = jest.fn();

  get = key => this[key];
}

export default Viewer;
