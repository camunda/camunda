/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {
  createProcessDefinition,
  createUser,
  mockProcessXML,
  searchResult,
} from 'modules/testUtils';
import {Filters} from '../index';
import {
  selectFlowNode,
  selectProcess,
} from 'modules/testUtils/selectComboBoxOption';
import {Paths} from 'modules/Routes';
import {useEffect} from 'react';
import {batchModificationStore} from 'modules/stores/batchModification';
import {MemoryRouter} from 'react-router-dom';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {ProcessDefinitionKeyContext} from '../../processDefinitionKeyContext';
import {mockMe} from 'modules/mocks/api/v2/me';
import {mockSearchProcessDefinitions} from 'modules/mocks/api/v2/processDefinitions/searchProcessDefinitions';

function getWrapper(
  initialPath: string = `${Paths.processes()}?active=true&incidents=true`,
) {
  const MockApp: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        batchModificationStore.reset();
      };
    }, []);

    return (
      <ProcessDefinitionKeyContext.Provider value="123">
        <QueryClientProvider client={new QueryClient()}>
          <MemoryRouter initialEntries={[initialPath]}>
            {children}
            <button onClick={batchModificationStore.enable}>
              Enter Batch Modification Mode
            </button>
          </MemoryRouter>
        </QueryClientProvider>
      </ProcessDefinitionKeyContext.Provider>
    );
  };

  return MockApp;
}

describe('Filters', () => {
  beforeEach(async () => {
    mockSearchProcessDefinitions().withSuccess(
      searchResult([
        createProcessDefinition({version: 2}),
        createProcessDefinition({version: 1}),
      ]),
    );
    mockSearchProcessDefinitions().withSuccess(
      searchResult([createProcessDefinition({version: 2})]),
    );
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockMe().withSuccess(createUser());
  });

  it('should disable fields in batch modification mode', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(screen.getByRole('combobox', {name: 'Name'})).toBeEnabled(),
    );

    await selectProcess({user, option: 'Big variable process'});
    await selectFlowNode({user, option: 'Start Event 1'});

    expect(screen.getByRole('combobox', {name: /version/i})).toBeEnabled();
    expect(screen.getByRole('combobox', {name: /element/i})).toBeEnabled();

    await user.click(
      screen.getByRole('button', {name: /enter batch modification mode/i}),
    );

    expect(screen.getByRole('combobox', {name: /name/i})).toBeDisabled();
    expect(screen.getByRole('combobox', {name: /version/i})).toBeDisabled();
    expect(screen.getByRole('combobox', {name: /element/i})).toBeDisabled();
  });
});
