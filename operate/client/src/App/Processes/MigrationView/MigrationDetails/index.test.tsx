/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {processesStore} from 'modules/stores/processes/processes.migration';
import {act, useEffect} from 'react';
import {MemoryRouter} from 'react-router-dom';

import {MigrationDetails} from '.';
import {mockProcessDefinitions} from 'modules/testUtils';
import {mockSearchProcessDefinitions} from 'modules/mocks/api/v2/processDefinitions/searchProcessDefinitions';

function createWrapper() {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      processInstanceMigrationStore.enable();
      processesStore.init();
      processesStore.fetchProcesses();
      return () => {
        processInstanceMigrationStore.reset();
        processesStore.reset();
      };
    }, []);
    return <MemoryRouter>{children}</MemoryRouter>;
  };
  return Wrapper;
}

describe('MigrationDetails', () => {
  it('should render migration details', async () => {
    const queryString =
      '?active=true&incidents=true&process=demoProcess&version=3';

    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
    });

    processInstanceMigrationStore.setSelectedInstancesCount(7);
    processInstanceMigrationStore.setCurrentStep('summary');
    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions);

    render(<MigrationDetails />, {wrapper: createWrapper()});

    await waitFor(() => {
      expect(processesStore.state.status).toBe('fetched');
    });

    act(() => {
      processesStore.setSelectedTargetProcess('{bigVarProcess}-{<default>}');
      processesStore.setSelectedTargetVersion(1);
    });

    expect(
      screen.getByText(
        /You are about to migrate 7 process instances from the process definition:/i,
      ),
    ).toBeInTheDocument();

    expect(
      screen.getByText(/New demo process - version 3/i),
    ).toBeInTheDocument();

    expect(screen.getByText(/to the process definition:/i)).toBeInTheDocument();

    expect(
      screen.getByText(/Big variable process - version 1/i),
    ).toBeInTheDocument();
  });
});
