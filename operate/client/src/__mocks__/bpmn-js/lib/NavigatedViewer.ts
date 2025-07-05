/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const diObject = {set: vi.fn()};

const createMockedModules = (container: any): {[module: string]: any} => ({
  canvas: {
    zoom: vi.fn(),
    addMarker: vi.fn(),
    removeMarker: vi.fn(),
    resized: vi.fn(),
    getRootElement: vi.fn(() => container.innerHTML),
    findRoot: vi.fn(),
  },
  zoomScroll: {stepZoom: vi.fn()},

  elementRegistry: {
    getGraphics: vi.fn(() => ({
      querySelector: vi.fn(() => ({setAttribute: vi.fn()})),
      getBoundingClientRect: vi.fn(() => ({
        x: 0,
        y: 0,
        height: 0,
        width: 0,
      })),
      getBBox: vi.fn(() => ({x: 0, y: 0, height: 0, width: 0})),
    })),
    get: vi.fn((id) => ({di: diObject, businessObject: {name: id}})),
    forEach: vi.fn(() => {}),
    filter: vi.fn(() => []),
  },
  graphicsFactory: {update: vi.fn(() => {})},
  eventBus: {on: vi.fn()},
  overlays: {
    add: vi.fn((_elementId: string, _type: string, {html: children}: any) => {
      container.appendChild(children);
    }),
    remove: vi.fn(),
    clear: vi.fn(),
  },
});

class Viewer {
  bpmnRenderer: any;
  container: any;
  constructor({container, bpmnRenderer}: any = {}) {
    this.container = container;
    this.bpmnRenderer = bpmnRenderer;
  }

  importDefinitions = vi.fn(() => Promise.resolve({}));
  importXML = vi.fn(() => {
    this.container.innerHTML = 'Diagram mock';
    return Promise.resolve({});
  });
  detach = vi.fn();
  destroy = vi.fn();
  on = vi.fn();
  off = vi.fn();

  get = (module: string) => createMockedModules(this.container)[module];
}

export default Viewer;
