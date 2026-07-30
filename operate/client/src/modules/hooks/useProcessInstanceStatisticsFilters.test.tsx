/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook} from '@testing-library/react';
import {MemoryRouter} from 'react-router';
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

  it('should not add variables when no variable is provided', () => {
    const {result} = renderHook(() => useProcessInstanceStatisticsFilters(), {
      wrapper: getWrapper({active: 'true'}),
    });

    expect(result.current.filter).toEqual({
      state: {$eq: 'ACTIVE'},
      hasIncident: false,
    });
  });

  it('should not add variables when the variable has no valid values', () => {
    const variable = {name: 'status', values: []};

    const {result} = renderHook(
      () => useProcessInstanceStatisticsFilters(variable),
      {wrapper: getWrapper({active: 'true'})},
    );

    expect(result.current.filter).not.toHaveProperty('variables');
  });

  it('should map a single variable value into filter.variables', () => {
    const variable = {name: 'status', values: ['"active"']};

    const {result} = renderHook(
      () => useProcessInstanceStatisticsFilters(variable),
      {wrapper: getWrapper({active: 'true'})},
    );

    expect(result.current.filter?.variables).toEqual([
      {name: 'status', value: '"active"'},
    ]);
  });

  it('should map multiple variable values with an $in operator', () => {
    const variable = {name: 'status', values: ['"active"', '"inactive"']};

    const {result} = renderHook(
      () => useProcessInstanceStatisticsFilters(variable),
      {wrapper: getWrapper({active: 'true'})},
    );

    expect(result.current.filter?.variables).toEqual([
      {name: 'status', value: {$in: ['"active"', '"inactive"']}},
    ]);
  });

  it('should strip process-definition fields while keeping variables', () => {
    const variable = {name: 'status', values: ['"active"']};

    const {result} = renderHook(
      () => useProcessInstanceStatisticsFilters(variable),
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
    expect(filter?.variables).toEqual([{name: 'status', value: '"active"'}]);
  });
});
