/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BusinessObject, ElementType} from 'bpmn-js/lib/NavigatedViewer';
import {
  isCallActivity,
  isBusinessRuleTask,
  isElementRunning,
  isDrillDownCandidate,
} from './drilldown';

function createBusinessObject(
  type: ElementType,
  overrides?: Partial<BusinessObject>,
): BusinessObject {
  return {
    id: 'element_1',
    name: 'Test Element',
    $type: type,
    ...overrides,
  } satisfies BusinessObject;
}

describe('drilldown utilities', () => {
  it('isCallActivity should return true for bpmn:CallActivity', () => {
    expect(isCallActivity(createBusinessObject('bpmn:CallActivity'))).toBe(
      true,
    );
  });

  it('isCallActivity should return false for bpmn:SubProcess', () => {
    expect(isCallActivity(createBusinessObject('bpmn:SubProcess'))).toBe(false);
  });

  it('isCallActivity should return false for bpmn:ServiceTask', () => {
    expect(isCallActivity(createBusinessObject('bpmn:ServiceTask'))).toBe(
      false,
    );
  });

  it('isCallActivity should return false for bpmn:BusinessRuleTask', () => {
    expect(isCallActivity(createBusinessObject('bpmn:BusinessRuleTask'))).toBe(
      false,
    );
  });

  it('isCallActivity should return false for bpmn:AdHocSubProcess', () => {
    expect(isCallActivity(createBusinessObject('bpmn:AdHocSubProcess'))).toBe(
      false,
    );
  });

  it('isBusinessRuleTask should return true for bpmn:BusinessRuleTask', () => {
    expect(
      isBusinessRuleTask(createBusinessObject('bpmn:BusinessRuleTask')),
    ).toBe(true);
  });

  it('isBusinessRuleTask should return false for bpmn:ServiceTask', () => {
    expect(isBusinessRuleTask(createBusinessObject('bpmn:ServiceTask'))).toBe(
      false,
    );
  });

  it('isBusinessRuleTask should return false for bpmn:CallActivity', () => {
    expect(isBusinessRuleTask(createBusinessObject('bpmn:CallActivity'))).toBe(
      false,
    );
  });

  it('isElementRunning should return true when running count is greater than 0', () => {
    expect(isElementRunning('el_1', {el_1: 3})).toBe(true);
  });

  it('isElementRunning should return false when running count is 0', () => {
    expect(isElementRunning('el_1', {el_1: 0})).toBe(false);
  });

  it('isElementRunning should return false when element is not in the map', () => {
    expect(isElementRunning('el_1', {})).toBe(false);
  });

  it('isElementRunning should return false when element is not in the map with other elements', () => {
    expect(isElementRunning('el_1', {el_2: 5})).toBe(false);
  });

  it('isDrillDownCandidate should return true for a running CallActivity', () => {
    const bo = createBusinessObject('bpmn:CallActivity');
    expect(isDrillDownCandidate('el_1', bo, {el_1: 2})).toBe(true);
  });

  it('isDrillDownCandidate should return false for a non-running CallActivity', () => {
    const bo = createBusinessObject('bpmn:CallActivity');
    expect(isDrillDownCandidate('el_1', bo, {el_1: 0})).toBe(false);
  });

  it('isDrillDownCandidate should return false for a CallActivity not in running map', () => {
    const bo = createBusinessObject('bpmn:CallActivity');
    expect(isDrillDownCandidate('el_1', bo, {})).toBe(false);
  });

  it('isDrillDownCandidate should return false for a running SubProcess', () => {
    const bo = createBusinessObject('bpmn:SubProcess');
    expect(isDrillDownCandidate('el_1', bo, {el_1: 2})).toBe(false);
  });

  it('isDrillDownCandidate should return false for a running ServiceTask', () => {
    const bo = createBusinessObject('bpmn:ServiceTask');
    expect(isDrillDownCandidate('el_1', bo, {el_1: 1})).toBe(false);
  });

  it('isDrillDownCandidate should return true for a running BusinessRuleTask', () => {
    const bo = createBusinessObject('bpmn:BusinessRuleTask');
    expect(isDrillDownCandidate('el_1', bo, {el_1: 1})).toBe(true);
  });

  it('isDrillDownCandidate should return false for a non-running BusinessRuleTask', () => {
    const bo = createBusinessObject('bpmn:BusinessRuleTask');
    expect(isDrillDownCandidate('el_1', bo, {el_1: 0})).toBe(false);
  });
});
