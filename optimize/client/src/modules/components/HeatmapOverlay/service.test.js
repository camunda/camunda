/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {isExcluded} from './service';

// creates a mock element whose businessObject.$instanceOf returns true for the given bpmn types,
// mirroring how the isBpmnType helper inspects elements
function createElement(bpmnTypes, {collapsed = false, type = 'shape'} = {}) {
  return {
    type,
    collapsed,
    businessObject: {
      $instanceOf: (queriedType) => bpmnTypes.includes(queriedType),
    },
  };
}

describe('isExcluded', () => {
  it('should not exclude an expanded ad-hoc subprocess when the flag is set', () => {
    // given
    const element = createElement(['bpmn:SubProcess', 'bpmn:AdHocSubProcess']);

    // when
    const excluded = isExcluded(element, true);

    // then
    expect(excluded).toBe(false);
  });

  it('should exclude an expanded ad-hoc subprocess when the flag is not set', () => {
    // given
    const element = createElement(['bpmn:SubProcess', 'bpmn:AdHocSubProcess']);

    // when
    const excluded = isExcluded(element);

    // then
    expect(excluded).toBe(true);
  });

  it('should still exclude a regular expanded subprocess when the flag is set', () => {
    // given
    const element = createElement(['bpmn:SubProcess']);

    // when
    const excluded = isExcluded(element, true);

    // then
    expect(excluded).toBe(true);
  });

  it('should not exclude a collapsed subprocess', () => {
    // given
    const element = createElement(['bpmn:SubProcess'], {collapsed: true});

    // when
    const excluded = isExcluded(element);

    // then
    expect(excluded).toBe(false);
  });
});
