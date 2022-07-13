/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {diObject} from 'modules/testUtils';

const mockedModules: {[module: string]: any} = {
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
    get: jest.fn((id) => ({di: diObject, businessObject: {name: id}})),
    forEach: jest.fn(() => {}),
    filter: jest.fn(() => []),
  },
  graphicsFactory: {update: jest.fn(() => {})},
  eventBus: {on: jest.fn()},
  overlays: {
    add: jest.fn((elementId, type, {html: children}) => {
      document.body.appendChild(children);
    }),
    remove: jest.fn(),
    clear: jest.fn(),
  },
};

class Viewer {
  bpmnRenderer: any;
  container: any;
  constructor({container, bpmnRenderer}: any = {}) {
    this.container = container;
    this.bpmnRenderer = bpmnRenderer;
  }

  importDefinitions = jest.fn(() => Promise.resolve({}));
  importXML = jest.fn(() => {
    this.container.innerHTML = 'Diagram mock';
    return Promise.resolve({});
  });
  detach = jest.fn();
  destroy = jest.fn();
  on = jest.fn();
  off = jest.fn();

  get = (module: string) => mockedModules[module];
}

export default Viewer;
