/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {variableFilterStore} from './variableFilter';

describe('variableFilterStore', () => {
  afterEach(() => {
    variableFilterStore.reset();
  });

  it('should have default state', () => {
    expect(variableFilterStore.conditions).toEqual([]);
    expect(variableFilterStore.hasActiveFilters).toBe(false);
    expect(variableFilterStore.state.isInMultipleMode).toBe(false);
  });

  it('should set conditions', () => {
    variableFilterStore.setConditions([
      {id: '1', name: 'status', operator: 'equals', value: '"active"'},
    ]);

    expect(variableFilterStore.conditions).toEqual([
      {id: '1', name: 'status', operator: 'equals', value: '"active"'},
    ]);
    expect(variableFilterStore.hasActiveFilters).toBe(true);
  });

  it('should set isInMultipleMode when multiple conditions are set', () => {
    variableFilterStore.setConditions([
      {id: '1', name: 'status', operator: 'equals', value: '"active"'},
      {id: '2', name: 'region', operator: 'equals', value: '"EU"'},
    ]);

    expect(variableFilterStore.state.isInMultipleMode).toBe(true);
  });

  it('should not set isInMultipleMode for single condition', () => {
    variableFilterStore.setConditions([
      {id: '1', name: 'status', operator: 'equals', value: '"active"'},
    ]);

    expect(variableFilterStore.state.isInMultipleMode).toBe(false);
  });

  it('should not update state when conditions are equal', () => {
    const conditions = [
      {id: '1', name: 'status', operator: 'equals' as const, value: '"active"'},
    ];

    variableFilterStore.setConditions(conditions);
    const stateAfterFirstSet = variableFilterStore.state.conditions;

    variableFilterStore.setConditions([...conditions]);
    expect(variableFilterStore.state.conditions).toBe(stateAfterFirstSet);
  });

  it('should reset to default state', () => {
    variableFilterStore.setConditions([
      {id: '1', name: 'status', operator: 'equals', value: '"active"'},
      {id: '2', name: 'region', operator: 'equals', value: '"EU"'},
    ]);

    expect(variableFilterStore.hasActiveFilters).toBe(true);
    expect(variableFilterStore.state.isInMultipleMode).toBe(true);

    variableFilterStore.reset();

    expect(variableFilterStore.conditions).toEqual([]);
    expect(variableFilterStore.hasActiveFilters).toBe(false);
    expect(variableFilterStore.state.isInMultipleMode).toBe(false);
  });

  it('should set isInMultipleMode manually', () => {
    variableFilterStore.setIsInMultipleMode(true);
    expect(variableFilterStore.state.isInMultipleMode).toBe(true);

    variableFilterStore.setIsInMultipleMode(false);
    expect(variableFilterStore.state.isInMultipleMode).toBe(false);
  });

  it('should derive variableWithValidatedValues from first equals condition', () => {
    variableFilterStore.setConditions([
      {id: '1', name: 'status', operator: 'equals', value: '"active"'},
    ]);

    expect(variableFilterStore.variableWithValidatedValues).toEqual({
      name: 'status',
      values: ['"active"'],
    });
  });

  it('should return undefined for variableWithValidatedValues when no equals condition', () => {
    variableFilterStore.setConditions([
      {id: '1', name: 'status', operator: 'exists', value: ''},
    ]);

    expect(variableFilterStore.variableWithValidatedValues).toBeUndefined();
  });

  it('should return undefined for variableWithValidatedValues when name is empty', () => {
    variableFilterStore.setConditions([
      {id: '1', name: '', operator: 'equals', value: '"active"'},
    ]);

    expect(variableFilterStore.variableWithValidatedValues).toBeUndefined();
  });

  it('should return undefined for variableWithValidatedValues when no conditions', () => {
    expect(variableFilterStore.variableWithValidatedValues).toBeUndefined();
  });
});
