/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import type {VariableCondition} from 'modules/stores/variableFilter';
import {useProcessInstanceStatisticsFilters} from './useProcessInstanceStatisticsFilters';

const getWrapper = (initialSearchParams?: Record<string, string>) => {
  const Wrapper = ({children}: {children: React.ReactNode}) => {
    const searchParams = new URLSearchParams(initialSearchParams);

    return (
      <MemoryRouter initialEntries={[`/processes?${searchParams.toString()}`]}>
        {children}
      </MemoryRouter>
    );
  };
  return Wrapper;
};

describe('useProcessInstanceStatisticsFilters', () => {
  it('should return an undefined filter when no base filter is present', () => {
    const {result} = renderHook(() => useProcessInstanceStatisticsFilters(), {
      wrapper: getWrapper(),
    });

    expect(result.current).toEqual({filter: undefined});
  });

  it('should not add variables when no conditions are provided', () => {
    const {result} = renderHook(() => useProcessInstanceStatisticsFilters(), {
      wrapper: getWrapper({active: 'true'}),
    });

    expect(result.current.filter).toEqual({
      state: {$eq: 'ACTIVE'},
      hasIncident: false,
    });
  });

  it('should not add variables for an empty conditions array', () => {
    const {result} = renderHook(() => useProcessInstanceStatisticsFilters([]), {
      wrapper: getWrapper({active: 'true'}),
    });

    expect(result.current.filter).not.toHaveProperty('variables');
  });

  it('should map variable conditions into filter.variables', () => {
    const conditions: VariableCondition[] = [
      {name: 'status', operator: 'equals', value: 'active'},
    ];

    const {result} = renderHook(
      () => useProcessInstanceStatisticsFilters(conditions),
      {wrapper: getWrapper({active: 'true'})},
    );

    expect(result.current.filter?.variables).toEqual([
      {name: 'status', value: {$eq: '"active"'}},
    ]);
  });

  it('should map multiple conditions with mixed operators', () => {
    const conditions: VariableCondition[] = [
      {name: 'status', operator: 'equals', value: 'active'},
      {name: 'region', operator: 'contains', value: 'eu'},
      {name: 'priority', operator: 'exists', value: ''},
    ];

    const {result} = renderHook(
      () => useProcessInstanceStatisticsFilters(conditions),
      {wrapper: getWrapper({active: 'true'})},
    );

    expect(result.current.filter?.variables).toEqual([
      {name: 'status', value: {$eq: '"active"'}},
      {name: 'region', value: {$like: '*eu*'}},
      {name: 'priority', value: {$exists: true}},
    ]);
  });

  it('should drop unparseable conditions and keep valid ones', () => {
    const conditions: VariableCondition[] = [
      {name: 'broken', operator: 'equals', value: '"NEW'},
      {name: 'status', operator: 'equals', value: 'active'},
    ];

    const {result} = renderHook(
      () => useProcessInstanceStatisticsFilters(conditions),
      {wrapper: getWrapper({active: 'true'})},
    );

    expect(result.current.filter?.variables).toEqual([
      {name: 'status', value: {$eq: '"active"'}},
    ]);
  });

  it('should strip process-definition fields while keeping variables', () => {
    const conditions: VariableCondition[] = [
      {name: 'status', operator: 'equals', value: 'active'},
    ];

    const {result} = renderHook(
      () => useProcessInstanceStatisticsFilters(conditions),
      {
        wrapper: getWrapper({
          active: 'true',
          processDefinitionId: 'my-process',
          processDefinitionVersion: '2',
        }),
      },
    );

    const filter = result.current.filter;
    expect(filter).not.toHaveProperty('processDefinitionId');
    expect(filter).not.toHaveProperty('processDefinitionVersion');
    expect(filter?.variables).toEqual([
      {name: 'status', value: {$eq: '"active"'}},
    ]);
  });
});
