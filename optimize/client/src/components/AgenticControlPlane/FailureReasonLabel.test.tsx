/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import {FailureKindTag, failureElement, limitLabel} from './FailureReasonLabel';
import type {FailureReason} from './service';

function reason(overrides: Partial<FailureReason>): FailureReason {
  return {
    kind: 'AGENT_INCIDENT',
    limit: null,
    flowNodeId: 'review',
    toolName: null,
    errorType: 'JOB_NO_RETRIES',
    message: null,
    runs: 1,
    ...overrides,
  };
}

it('should name the element of each kind of failure reason', () => {
  expect(failureElement(reason({kind: 'LIMIT', limit: 'TOKENS', flowNodeId: null}))).toBe(
    'Limit hit: max tokens'
  );
  expect(failureElement(reason({kind: 'AGENT_INCIDENT'}))).toBe('Agent element');
  expect(
    failureElement(reason({kind: 'TOOL_INCIDENT', flowNodeId: 'lookup', toolName: 'Look up'}))
  ).toBe('Tool: Look up');
  expect(failureElement(reason({kind: 'TOOL_INCIDENT', flowNodeId: 'lookup'}))).toBe(
    'Tool: lookup'
  );
});

it('should label the limits stored on agent runs', () => {
  expect(limitLabel('MODEL_CALLS')).toBe('max model calls');
  expect(limitLabel('TOOL_CALLS')).toBe('max tool calls');
  expect(limitLabel('OTHER')).toBe('OTHER');
});

it('should show the kind of a failure reason', () => {
  expect(shallow(<FailureKindTag kind="TOOL_INCIDENT" />).text()).toContain('Tool incident');
});
