/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {renderHook} from '@testing-library/react';
import {useBatchModificationStatisticsFilter} from './useBatchModificationStatisticsFilter';
import {processInstancesSelectionStore} from 'modules/stores/instancesSelection';

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

describe('useBatchModificationStatisticsFilter', () => {
  beforeEach(() => {
    processInstancesSelectionStore.setRuntime({
      totalCount: 4,
      visibleIds: ['1', '2', '3', '4'],
    });
  });

  afterEach(() => {
    processInstancesSelectionStore.reset();
  });

  it('should not restrict by processInstanceKey in ALL mode', () => {
    processInstancesSelectionStore.selectAll();

    const {result} = renderHook(() => useBatchModificationStatisticsFilter(), {
      wrapper: getWrapper({
        active: 'true',
      }),
    });

    expect(result.current.filter?.processInstanceKey).toBeUndefined();
    expect(result.current.filter).toEqual({
      hasIncident: false,
      state: {$eq: 'ACTIVE'},
    });
  });

  it('should restrict by $in in INCLUDE mode', () => {
    processInstancesSelectionStore.select('1');
    processInstancesSelectionStore.select('3');

    const {result} = renderHook(() => useBatchModificationStatisticsFilter(), {
      wrapper: getWrapper({
        active: 'true',
      }),
    });

    expect(result.current.filter?.processInstanceKey).toEqual({
      $in: ['1', '3'],
    });
  });

  it('should restrict by $notIn in EXCLUDE mode', () => {
    processInstancesSelectionStore.selectAll();
    processInstancesSelectionStore.select('2');

    const {result} = renderHook(() => useBatchModificationStatisticsFilter(), {
      wrapper: getWrapper({
        active: 'true',
      }),
    });

    expect(result.current.filter?.processInstanceKey).toEqual({$notIn: ['2']});
  });
});
