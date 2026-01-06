/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {isMigratableFlowNode} from './isMigratableFlowNode';

describe('isMigratableFlowNode', () => {
  it('should return true for ad hoc sub process', () => {
    const businessObject = {
      id: 'adHocSubProcess',
      $type: 'bpmn:AdHocSubProcess',
    } as BusinessObject;

    expect(isMigratableFlowNode(businessObject)).toBe(true);
  });
});
