/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {processesStore} from 'modules/stores/processes/processes.list';
import {
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessXML,
} from 'modules/testUtils';
import {Filters} from '../index';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';

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

function getWrapper(
  initialPath: string = `${Paths.processes()}?active=true&incidents=true`,
) {
  const MockApp: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        processesStore.reset();
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
    mockFetchGroupedProcesses().withSuccess(
      groupedProcessesMock.filter(({tenantId}) => tenantId === '<default>'),
    );
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    processesStore.fetchProcesses();
  });

  it('should disable fields in batch modification mode', async () => {
    const {user} = render(<Filters />, {
      wrapper: getWrapper(),
    });

    await waitFor(() => expect(screen.getByLabelText('Name')).toBeEnabled());

    await selectProcess({user, option: 'Big variable process'});
    await selectFlowNode({user, option: 'Start Event 1'});

    expect(screen.getByRole('combobox', {name: /version/i})).toBeEnabled();
    expect(screen.getByRole('combobox', {name: /flow node/i})).toBeEnabled();

    await user.click(
      screen.getByRole('button', {name: /enter batch modification mode/i}),
    );

    expect(screen.getByRole('combobox', {name: /name/i})).toBeDisabled();
    expect(screen.getByRole('combobox', {name: /version/i})).toBeDisabled();
    expect(screen.getByRole('combobox', {name: /flow node/i})).toBeDisabled();
  });
});
