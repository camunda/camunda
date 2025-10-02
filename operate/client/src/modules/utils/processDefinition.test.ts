/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/*
 * Unit tests for processDefinition utils
 */
import {getDiagramNameByProcessDefinition} from './processDefinition';
import type {ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.8';

describe('getDiagramNameByProcessDefinition', () => {
  it('returns formatted name with version', () => {
    const definition = {
      name: 'Order Process',
      processDefinitionId: 'order-process-id',
      version: 5,
    };
    expect(
      getDiagramNameByProcessDefinition(definition as ProcessDefinition),
    ).toBe('Order Process v5');
  });

  it('uses processDefinitionId if name is missing', () => {
    const definition = {
      name: undefined,
      processDefinitionId: 'fallback-id',
      version: 2,
    };
    expect(
      getDiagramNameByProcessDefinition(definition as ProcessDefinition),
    ).toBe('fallback-id v2');
  });

  it('handles empty name and processDefinitionId', () => {
    const definition = {
      name: '',
      processDefinitionId: '',
      version: 0,
    };
    expect(
      getDiagramNameByProcessDefinition(definition as ProcessDefinition),
    ).toBe(' v0');
  });
});
