/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ElementType, BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {getFlowNodes} from '..';

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

describe('getFlowNodes', () => {
  it('should get flow nodes', () => {
    const Task1 = createElement('bpmn:ServiceTask', 'Task1');
    const Root = createElement('bpmn:Process', 'Root');
    const SequenceFlow1 = createElement('bpmn:SequenceFlow', 'SequenceFlow1');

    const elements = {task1: Task1, root: Root, sequenceFlow1: SequenceFlow1};

    expect(getFlowNodes(elements)).toEqual([Task1]);
  });

  it('should get empty objects', () => {
    expect(getFlowNodes({})).toEqual([]);
    expect(getFlowNodes()).toEqual([]);
  });
});
