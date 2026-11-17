/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {renderHook} from '@testing-library/react';
import {useMigrationStatisticsFilter} from './useMigrationStatisticsFilter';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';

const getWrapper = (initialSearchParams?: Record<string, string>) => {
  const Wrapper = ({children}: {children: React.ReactNode}) => {
    const searchParams = new URLSearchParams(initialSearchParams);

    return (
      <MemoryRouter initialEntries={[`/processes?${searchParams.toString()}`]}>
        <Routes>
          <Route path="/processes" element={children} />
        </Routes>
      </MemoryRouter>
    );
  };
  return Wrapper;
};

describe('useMigrationStatisticsFilter', () => {
  afterEach(() => {
    processInstanceMigrationStore.reset();
  });

  it('should include the element selection from the URL', () => {
    processInstanceMigrationStore.setBatchOperationQuery({
      ids: [],
      excludeIds: [],
    });

    const {result} = renderHook(() => useMigrationStatisticsFilter(), {
      wrapper: getWrapper({
        active: 'true',
        elementId: 'ViewFiles',
      }),
    });

    expect(result.current.filter?.processInstanceKey).toBeUndefined();
    expect(result.current.filter).toEqual({
      state: {$eq: 'ACTIVE'},
      hasIncident: false,
      elementId: {$eq: 'ViewFiles'},
      elementInstanceState: {$eq: 'ACTIVE'},
    });
  });

  it('should not restrict instance keys in ALL mode', () => {
    processInstanceMigrationStore.setBatchOperationQuery({
      ids: [],
      excludeIds: [],
    });

    const {result} = renderHook(() => useMigrationStatisticsFilter(), {
      wrapper: getWrapper({active: 'true'}),
    });

    expect(result.current.filter?.processInstanceKey).toBeUndefined();
  });

  it('should restrict by $in in INCLUDE mode', () => {
    processInstanceMigrationStore.setBatchOperationQuery({
      ids: ['1', '3'],
      excludeIds: [],
    });

    const {result} = renderHook(() => useMigrationStatisticsFilter(), {
      wrapper: getWrapper({active: 'true'}),
    });

    expect(result.current.filter?.processInstanceKey).toEqual({
      $in: ['1', '3'],
    });
  });

  it('should restrict by $notIn in EXCLUDE mode', () => {
    processInstanceMigrationStore.setBatchOperationQuery({
      ids: [],
      excludeIds: ['2'],
    });

    const {result} = renderHook(() => useMigrationStatisticsFilter(), {
      wrapper: getWrapper({active: 'true'}),
    });

    expect(result.current.filter?.processInstanceKey).toEqual({$notIn: ['2']});
  });
});
