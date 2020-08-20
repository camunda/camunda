/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* eslint-disable react/prop-types */

import useOperationApply from './useOperationApply';
import {renderHook} from '@testing-library/react-hooks';

import React from 'react';

import FilterContext from 'modules/contexts/FilterContext';
import {instanceSelection} from 'modules/stores/instanceSelection';
import {INSTANCE_SELECTION_MODE} from 'modules/constants';
import {
  mockUseDataManager,
  mockData,
  mockUseInstancesPollContext,
} from './useOperationApply.setup';

const OPERATION_TYPE = 'DUMMY';

jest.mock('modules/hooks/useDataManager', () => () => mockUseDataManager);
jest.mock('modules/contexts/InstancesPollContext', () => ({
  useInstancesPollContext: () => mockUseInstancesPollContext,
}));

function renderUseOperationApply({filterContext}) {
  const {result} = renderHook(() => useOperationApply(), {
    wrapper: ({children}) => (
      <FilterContext.Provider value={filterContext}>
        {children}
      </FilterContext.Provider>
    ),
  });

  result.current.applyBatchOperation(OPERATION_TYPE);
}

describe('useOperationApply', () => {
  beforeEach(() => {
    jest.resetAllMocks();
  });
  afterEach(() => {
    instanceSelection.reset();
  });

  it('should call apply (no filter, select all ids)', () => {
    const {expectedQuery, ...context} = mockData.noFilterSelectAll;

    renderUseOperationApply(context);

    expect(mockUseDataManager.applyBatchOperation).toHaveBeenCalledWith(
      OPERATION_TYPE,
      expectedQuery
    );
  });

  it('should call apply (set id filter, select all ids)', () => {
    const {expectedQuery, ...context} = mockData.setFilterSelectAll;
    instanceSelection.setAllChecked();
    renderUseOperationApply(context);

    expect(mockUseDataManager.applyBatchOperation).toHaveBeenCalledWith(
      OPERATION_TYPE,
      expectedQuery
    );
  });

  it('should call apply (set id filter, select one id)', () => {
    const {expectedQuery, ...context} = mockData.setFilterSelectOne;
    instanceSelection.selectInstance('1');

    renderUseOperationApply(context);

    expect(mockUseDataManager.applyBatchOperation).toHaveBeenCalledWith(
      OPERATION_TYPE,
      expectedQuery
    );
  });

  it('should call apply (set id filter, exclude one id)', () => {
    const {expectedQuery, ...context} = mockData.setFilterExcludeOne;
    instanceSelection.setMode(INSTANCE_SELECTION_MODE.EXCLUDE);
    instanceSelection.selectInstance('1');
    renderUseOperationApply(context);

    expect(mockUseDataManager.applyBatchOperation).toHaveBeenCalledWith(
      OPERATION_TYPE,
      expectedQuery
    );
  });

  it('should call apply (set workflow filter, select one)', () => {
    const {expectedQuery, ...context} = mockData.setWorkflowFilterSelectOne;
    instanceSelection.selectInstance('1');
    renderUseOperationApply(context);

    expect(mockUseDataManager.applyBatchOperation).toHaveBeenCalledWith(
      OPERATION_TYPE,
      expectedQuery
    );
  });

  it('should poll all visible instances', () => {
    const {expectedQuery, ...context} = mockData.setFilterSelectAll;

    renderUseOperationApply(context);

    expect(mockUseInstancesPollContext.addAllVisibleIds).toHaveBeenCalled();
  });

  it('should poll the selected instances', () => {
    const {expectedQuery, ...context} = mockData.setWorkflowFilterSelectOne;
    instanceSelection.selectInstance('1');

    renderUseOperationApply(context);

    expect(mockUseInstancesPollContext.addIds).toHaveBeenCalled();
  });
});
