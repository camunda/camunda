/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ElementType, BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {isFlowNode} from '..';

const createElement = (
  type: ElementType,
  id: string = 'FlowNode',
): BusinessObject => {
  return {
    id,
    name: 'Element',
    $type: type,
    $instanceOf: () => type === 'bpmn:ServiceTask',
  };
};

describe('isFlowNode', () => {
  it('should return true for tasks', () => {
    const element = createElement('bpmn:ServiceTask');

    expect(isFlowNode(element)).toBeTruthy();
  });

  it('should return false for Processes', () => {
    const element = createElement('bpmn:Process');

    expect(isFlowNode(element)).toBeFalsy();
  });
});
