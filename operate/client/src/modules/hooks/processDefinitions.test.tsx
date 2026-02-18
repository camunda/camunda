/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {searchResult, createProcessDefinition} from 'modules/testUtils';
import {mockSearchProcessDefinitions} from 'modules/mocks/api/v2/processDefinitions/searchProcessDefinitions';
import {
  getDefinitionIdentifier,
  getProcessDefinitionName,
  splitDefinitionIdentifier,
  useProcessDefinitions,
  useProcessDefinitionVersions,
  useProcessDefinitionSelection,
  useSelectedProcessDefinition,
  useProcessDefinitionNames,
} from './processDefinitions';

const getWrapper = (searchParams?: {process?: string; version?: string}) => {
  let initialPath = Paths.processes();
  if (searchParams) {
    const params = new URLSearchParams(searchParams);
    initialPath += `?${params.toString()}`;
  }
  const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path={Paths.processes()} element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
  return Wrapper;
};

describe('getDefinitionIdentifier', () => {
  it('should combine a processDefinitionId and tenantId into an identifier', () => {
    const identifier = getDefinitionIdentifier('testProcess', 'tenantA');
    expect(identifier).toBe('testProcess--tenantA');
  });

  it('should use DEFAULT_TENANT when tenantId is omitted', () => {
    const identifier = getDefinitionIdentifier('testProcess');
    expect(identifier).toBe('testProcess--<default>');
  });
});

describe('getProcessDefinitionName', () => {
  it('should return the name when present', () => {
    const name = getProcessDefinitionName({
      name: 'Test Process',
      processDefinitionId: 'testProcess',
    });
    expect(name).toBe('Test Process');
  });

  it('should fall back to processDefinitionId when name is undefined', () => {
    const name = getProcessDefinitionName({
      name: undefined,
      processDefinitionId: 'testProcess',
    });
    expect(name).toBe('testProcess');
  });
});

describe('splitDefinitionIdentifier', () => {
  it('should split an identifier into definitionId and tenantId', () => {
    const result = splitDefinitionIdentifier('testProcess--tenantA');
    expect(result).toEqual({
      definitionId: 'testProcess',
      tenantId: 'tenantA',
    });
  });

  it('should return both undefined for undefined input', () => {
    const result = splitDefinitionIdentifier(undefined);
    expect(result).toEqual({
      definitionId: undefined,
      tenantId: undefined,
    });
  });
});

describe('useProcessDefinitions', () => {
  it('should return process definitions sorted by label with additional identifier and label fields', async () => {
    mockSearchProcessDefinitions().withSuccess(
      searchResult([
        createProcessDefinition({
          processDefinitionId: 'processB',
          name: 'Process B',
        }),
        createProcessDefinition({
          processDefinitionId: 'processA',
          name: 'Process A',
        }),
      ]),
    );

    const {result} = renderHook(() => useProcessDefinitions(), {
      wrapper: getWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data![0].label).toBe('Process A');
    expect(result.current.data![0].identifier).toBe('processA--<default>');
    expect(result.current.data![1].label).toBe('Process B');
    expect(result.current.data![1].identifier).toBe('processB--<default>');
  });
});

describe('useProcessDefinitionVersions', () => {
  it('should return available versions with "all" prepended when multiple versions exist', async () => {
    mockSearchProcessDefinitions().withSuccess(
      searchResult([
        createProcessDefinition({version: 3}),
        createProcessDefinition({version: 2}),
        createProcessDefinition({version: 1}),
      ]),
    );

    const {result} = renderHook(() => useProcessDefinitionVersions('someId'), {
      wrapper: getWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(['all', 3, 2, 1]);
  });

  it('should return versions without "all" when only a single version exists', async () => {
    mockSearchProcessDefinitions().withSuccess(
      searchResult([createProcessDefinition({version: 1})]),
    );

    const {result} = renderHook(() => useProcessDefinitionVersions('someId'), {
      wrapper: getWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([1]);
  });

  it('should disable the query when no processDefinitionId is provided', () => {
    const {result} = renderHook(() => useProcessDefinitionVersions(), {
      wrapper: getWrapper(),
    });

    expect(result.current.isEnabled).toBeFalsy();
    expect(result.current.data).toBeUndefined();
  });
});

describe('useProcessDefinitionSelection', () => {
  it('should return "all-versions" when no specific version is filtered', async () => {
    mockSearchProcessDefinitions().withSuccess(
      searchResult([
        createProcessDefinition({
          processDefinitionId: 'testProcess',
          name: 'Test Process',
        }),
        createProcessDefinition({
          processDefinitionId: 'testProcess2',
          name: 'Second Test Process',
        }),
      ]),
    );

    const {result} = renderHook(() => useProcessDefinitionSelection(), {
      wrapper: getWrapper({process: 'testProcess', version: 'all'}),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual({
      kind: 'all-versions',
      definition: {
        name: 'Test Process',
        processDefinitionId: 'testProcess',
      },
    });
  });

  it('should return "single-version" when a specific version is filtered', async () => {
    const definition = createProcessDefinition({
      processDefinitionId: 'testProcess',
      name: 'Test Process',
      version: 2,
    });

    mockSearchProcessDefinitions().withSuccess(searchResult([definition]));

    const {result} = renderHook(() => useProcessDefinitionSelection(), {
      wrapper: getWrapper({process: 'testProcess', version: '2'}),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual({
      kind: 'single-version',
      definition,
    });
  });

  it('should return "no-match" when the API search returns no data', async () => {
    mockSearchProcessDefinitions().withSuccess(searchResult([]));

    const {result} = renderHook(() => useProcessDefinitionSelection(), {
      wrapper: getWrapper({process: 'unknown'}),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual({kind: 'no-match'});
  });

  it('should disable the query when no processDefinitionId is filtered', () => {
    const {result} = renderHook(() => useProcessDefinitionSelection(), {
      wrapper: getWrapper(),
    });

    expect(result.current.isEnabled).toBeFalsy();
    expect(result.current.data).toBeUndefined();
  });
});

describe('useSelectedProcessDefinition', () => {
  it('should return the definition when exactly one search result is found', async () => {
    const definition = createProcessDefinition({
      processDefinitionId: 'testProcess',
      name: 'Test Process',
      version: 1,
    });

    mockSearchProcessDefinitions().withSuccess(searchResult([definition]));

    const {result} = renderHook(() => useSelectedProcessDefinition(), {
      wrapper: getWrapper({process: 'testProcess', version: '1'}),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(definition);
  });

  it('should disable the query when no processDefinitionId is filtered', () => {
    const {result} = renderHook(() => useSelectedProcessDefinition(), {
      wrapper: getWrapper({version: '1'}),
    });

    expect(result.current.isEnabled).toBeFalsy();
    expect(result.current.data).toBeUndefined();
  });

  it('should disable the query when no version is filtered', () => {
    const {result} = renderHook(() => useSelectedProcessDefinition(), {
      wrapper: getWrapper({process: 'testProcess'}),
    });

    expect(result.current.isEnabled).toBeFalsy();
    expect(result.current.data).toBeUndefined();
  });
});

describe('useProcessDefinitionNames', () => {
  it('should return a map of all processDefinitionKeys and their resolved names', async () => {
    mockSearchProcessDefinitions().withSuccess(
      searchResult([
        createProcessDefinition({
          processDefinitionKey: '1',
          name: 'Process One',
          processDefinitionId: 'proc-1',
        }),
        createProcessDefinition({
          processDefinitionKey: '2',
          name: undefined,
          processDefinitionId: 'proc-2',
        }),
      ]),
    );

    const {result} = renderHook(() => useProcessDefinitionNames(), {
      wrapper: getWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual({
      1: 'Process One',
      2: 'proc-2',
    });
  });
});
