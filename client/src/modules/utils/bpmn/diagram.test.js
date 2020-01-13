/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {parseDiagramXML} from './bpmn';
import bpmnJs from 'bpmn-js';

jest.mock('bpmn-js', () => {
  const createModdleMock = jest
    .fn()
    .mockImplementation(() => ({fromXML: jest.fn()}));

  class bpmnJs {
    constructor(options) {
      this.options = options;
    }
  }

  bpmnJs.prototype._createModdle = createModdleMock;

  return bpmnJs;
});

describe('bpmn-js', () => {
  it('should call _createModdle function', () => {
    parseDiagramXML();
    jest.spyOn(bpmnJs.prototype, '_createModdle');

    expect(bpmnJs.prototype._createModdle).toHaveBeenCalled();
  });
  it('should return a Promise', () => {});
  it('should return a reject the Promise with an error message', () => {});
  it('should resolve the promise with an object containing definitions', () => {});
  it('should resolve the promise with an object containing bpmnElements', () => {});
});
