/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook} from '@testing-library/react-hooks';
import {processesStore} from 'modules/stores/processes/processes.migration';
import {useProcessId} from './useProcessId';

jest.mock('modules/stores/processes/processes.migration', () => ({
  processesStore: {
    getSelectedProcessDetails: jest.fn(),
    getProcessId: jest.fn(),
  },
}));

describe('useProcessId', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should return the processId when processName and version are defined', () => {
    (processesStore.getSelectedProcessDetails as jest.Mock).mockReturnValue({
      processName: 'mockProcess',
      version: '1',
    });
    (processesStore.getProcessId as jest.Mock).mockReturnValue('mockProcessId');

    const {result} = renderHook(() => useProcessId());

    expect(result.current).toBe('mockProcessId');
    expect(processesStore.getSelectedProcessDetails).toHaveBeenCalled();
    expect(processesStore.getProcessId).toHaveBeenCalledWith({
      process: 'mockProcess',
      version: '1',
    });
  });

  it('should return undefined when processName or version is undefined', () => {
    (processesStore.getSelectedProcessDetails as jest.Mock).mockReturnValue({
      processName: undefined,
      version: undefined,
    });
    (processesStore.getProcessId as jest.Mock).mockReturnValue(undefined);

    const {result} = renderHook(() => useProcessId());

    expect(result.current).toBeUndefined();
    expect(processesStore.getSelectedProcessDetails).toHaveBeenCalled();
    expect(processesStore.getProcessId).toHaveBeenCalledWith({
      process: undefined,
      version: undefined,
    });
  });

  it('should handle cases where getProcessId returns null', () => {
    (processesStore.getSelectedProcessDetails as jest.Mock).mockReturnValue({
      processName: 'mockProcess',
      version: '1',
    });
    (processesStore.getProcessId as jest.Mock).mockReturnValue(null);

    const {result} = renderHook(() => useProcessId());

    expect(result.current).toBeNull();
    expect(processesStore.getSelectedProcessDetails).toHaveBeenCalled();
    expect(processesStore.getProcessId).toHaveBeenCalledWith({
      process: 'mockProcess',
      version: '1',
    });
  });
});
