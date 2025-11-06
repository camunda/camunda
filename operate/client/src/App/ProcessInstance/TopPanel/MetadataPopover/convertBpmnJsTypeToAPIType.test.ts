/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {convertBpmnJsTypeToAPIType} from './convertBpmnJsTypeToAPIType';

describe('convertBpmnJsTypeToAPIType', () => {
  it('returns "UNSPECIFIED" for null input', () => {
    expect(convertBpmnJsTypeToAPIType(null)).toBe('UNSPECIFIED');
  });

  it('returns "UNSPECIFIED" for undefined input', () => {
    expect(convertBpmnJsTypeToAPIType(undefined)).toBe('UNSPECIFIED');
  });

  it('converts BPMN type with namespace prefix', () => {
    expect(convertBpmnJsTypeToAPIType('bpmn:UserTask')).toBe('USER_TASK');
    expect(convertBpmnJsTypeToAPIType('bpmn:ServiceTask')).toBe('SERVICE_TASK');
    expect(convertBpmnJsTypeToAPIType('bpmn:BusinessRuleTask')).toBe(
      'BUSINESS_RULE_TASK',
    );
  });
});
