/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

class Viewer {
  constructor({container, bpmnRenderer} = {}) {
    this.canvas = {
      zoom: jest.fn(),
      addMarker: jest.fn(),
      removeMarker: jest.fn(),
      resized: jest.fn(),
    };
    this.zoomScroll = {stepZoom: jest.fn()};
    this.container = container;
    this.bpmnRenderer = bpmnRenderer;
    this.elementRegistry = {
      getGraphics: jest.fn(() => ({
        querySelector: jest.fn(() => ({setAttribute: jest.fn()})),
      })),
    };
    this.eventBus = {on: jest.fn()};
    this.overlays = {add: jest.fn(), remove: jest.fn()};
  }

  importXML = jest.fn((_, callback) => {
    callback();
  });

  detach = jest.fn();

  get = (key) => this[key];
}

export default Viewer;
