/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {getParentElement} from './getParentElement';

const parentElement: BusinessObject = {
  id: 'subProcess',
  name: 'Sub Process',
  $type: 'bpmn:SubProcess',
};

const element: BusinessObject = {
  id: 'task',
  name: 'Task',
  $type: 'bpmn:ServiceTask',
  $parent: parentElement,
};

describe('getParentElement', () => {
  it('should return the parent element when a valid child element is provided', () => {
    const result = getParentElement(element);

    expect(result).toEqual(parentElement);
  });

  it('should return undefined when the child element has no parent', () => {
    const result = getParentElement(parentElement);

    expect(result).toBeUndefined();
  });

  it('should return undefined when the input is undefined', () => {
    expect(getParentElement(undefined)).toBeUndefined();
  });
});
