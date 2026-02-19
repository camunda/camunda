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
import {searchResult} from 'modules/testUtils';
import {createDecisionDefinition} from 'modules/mocks/mockDecisionDefinitions';
import {mockSearchDecisionDefinitions} from 'modules/mocks/api/v2/decisionDefinitions/searchDecisionDefinitions';
import {
  getDefinitionIdentifier,
  getDefinitionIdFromIdentifier,
  useDecisionDefinitions,
  useDecisionDefinitionVersions,
  useDecisionDefinitionSelection,
} from './decisionDefinitions';

const getWrapper = (searchParams?: {name?: string; version?: string}) => {
  let initialPath = Paths.decisions();
  if (searchParams) {
    const params = new URLSearchParams(searchParams);
    initialPath += `?${params.toString()}`;
  }
  const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path={Paths.decisions()} element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
  return Wrapper;
};

describe('getDefinitionIdentifier', () => {
  it('should combine a decisionDefinitionId and tenantId into an identifier', () => {
    const identifier = getDefinitionIdentifier('testDecision', 'tenantA');
    expect(identifier).toBe('testDecision##tenantA');
  });

  it('should use DEFAULT_TENANT when tenantId is omitted', () => {
    const identifier = getDefinitionIdentifier('testDecision');
    expect(identifier).toBe('testDecision##<default>');
  });
});

describe('getDefinitionIdFromIdentifier', () => {
  it('should extract the decisionDefinitionId from a given identifier', () => {
    const decisionDefinitionId = getDefinitionIdFromIdentifier(
      'testDecision##myTenant',
    );
    expect(decisionDefinitionId).toBe('testDecision');
  });

  it('should return undefined for undefined input', () => {
    expect(getDefinitionIdFromIdentifier(undefined)).toBeUndefined();
  });
});

describe('useDecisionDefinitions', () => {
  it('should return decision definitions sorted by name with additional identifier field', async () => {
    mockSearchDecisionDefinitions().withSuccess(
      searchResult([
        createDecisionDefinition({
          decisionDefinitionId: 'decisionB',
          name: 'Decision B',
        }),
        createDecisionDefinition({
          decisionDefinitionId: 'decisionA',
          name: 'Decision A',
        }),
      ]),
    );

    const {result} = renderHook(() => useDecisionDefinitions(), {
      wrapper: getWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data![0].name).toBe('Decision A');
    expect(result.current.data![0].identifier).toBe('decisionA##<default>');
    expect(result.current.data![1].name).toBe('Decision B');
    expect(result.current.data![1].identifier).toBe('decisionB##<default>');
  });
});

describe('useDecisionDefinitionVersions', () => {
  it('should return available versions with "all" prepended when multiple versions exist', async () => {
    mockSearchDecisionDefinitions().withSuccess(
      searchResult([
        createDecisionDefinition({version: 3}),
        createDecisionDefinition({version: 2}),
        createDecisionDefinition({version: 1}),
      ]),
    );

    const {result} = renderHook(() => useDecisionDefinitionVersions('someId'), {
      wrapper: getWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(['all', 3, 2, 1]);
  });

  it('should return versions without "all" when only a single version exists', async () => {
    mockSearchDecisionDefinitions().withSuccess(
      searchResult([createDecisionDefinition({version: 1})]),
    );

    const {result} = renderHook(() => useDecisionDefinitionVersions('someId'), {
      wrapper: getWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([1]);
  });

  it('should disable the query when no decisionDefinitionId is provided', () => {
    const {result} = renderHook(() => useDecisionDefinitionVersions(), {
      wrapper: getWrapper(),
    });

    expect(result.current.isEnabled).toBeFalsy();
    expect(result.current.data).toBeUndefined();
  });
});

describe('useDecisionDefinitionSelection', () => {
  it('should return "all-versions" when no specific version is filtered', async () => {
    mockSearchDecisionDefinitions().withSuccess(
      searchResult([
        createDecisionDefinition({
          decisionDefinitionId: 'testDecision',
          name: 'Test Decision',
        }),
        createDecisionDefinition({
          decisionDefinitionId: 'testDecision2',
          name: 'Second Test Decision',
        }),
      ]),
    );

    const {result} = renderHook(() => useDecisionDefinitionSelection(), {
      wrapper: getWrapper({name: 'testDecision', version: 'all'}),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual({
      kind: 'all-versions',
      definition: {
        name: 'Test Decision',
        decisionDefinitionId: 'testDecision',
      },
    });
  });

  it('should return "single-version" when a specific version is filtered', async () => {
    const definition = createDecisionDefinition({
      decisionDefinitionId: 'testDecision',
      name: 'Test Decision',
      version: 2,
    });

    mockSearchDecisionDefinitions().withSuccess(searchResult([definition]));

    const {result} = renderHook(() => useDecisionDefinitionSelection(), {
      wrapper: getWrapper({name: 'testDecision', version: '2'}),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual({
      kind: 'single-version',
      definition,
    });
  });

  it('should return "no-match" when the API search returns no data', async () => {
    mockSearchDecisionDefinitions().withSuccess(searchResult([]));

    const {result} = renderHook(() => useDecisionDefinitionSelection(), {
      wrapper: getWrapper({name: 'unknown'}),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual({kind: 'no-match'});
  });

  it('should disable the query when no decisionDefinitionId is filtered', () => {
    const {result} = renderHook(() => useDecisionDefinitionSelection(), {
      wrapper: getWrapper(),
    });

    expect(result.current.isEnabled).toBeFalsy();
    expect(result.current.data).toBeUndefined();
  });
});
