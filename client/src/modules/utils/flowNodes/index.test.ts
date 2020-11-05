/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {isFlowNode, getFlowNodes} from './index';

const createElement = (type: any, isFlowNode: any, id: any) => {
  return {
    id,
    businessObject: {
      $type: type,
      $instanceOf: () => isFlowNode,
    },
  };
};

describe('flowNodes', () => {
  describe('isFlowNode', () => {
    it('should return true for tasks', () => {
      // @ts-expect-error ts-migrate(2554) FIXME: Expected 3 arguments, but got 2.
      const element = createElement('bpmn:Task', true);

      expect(isFlowNode(element)).toBeTruthy();
    });

    it('should return false for Processes', () => {
      // @ts-expect-error ts-migrate(2554) FIXME: Expected 3 arguments, but got 2.
      const element = createElement('bpmn:Process', false);

      expect(isFlowNode(element)).toBeFalsy();
    });
  });

  describe('getFlowNodes', () => {
    it('should get flow nodes', () => {
      const Task1 = createElement('bpmn:Task', true, 'Task1');
      const Root = createElement('bpmn:Process', false, 'Root');
      const SequenceFlow1 = createElement(
        'bpmn:SequenceFlow',
        false,
        'SequenceFlow1'
      );

      const elements = [Task1, Root, SequenceFlow1];

      expect(getFlowNodes(elements)).toEqual([Task1]);
    });

    it('sould get empty objects', () => {
      expect(getFlowNodes([])).toEqual([]);
      // @ts-expect-error ts-migrate(2554) FIXME: Expected 1 arguments, but got 0.
      expect(getFlowNodes()).toEqual([]);
    });
  });
});
