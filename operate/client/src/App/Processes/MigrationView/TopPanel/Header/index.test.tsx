/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {Header} from '.';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {useEffect, act} from 'react';
import {MemoryRouter} from 'react-router-dom';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {groupedProcessesMock} from 'modules/testUtils';
import {processesStore} from 'modules/stores/processes/processes.migration';

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  useEffect(() => {
    return () => {
      processInstanceMigrationStore.reset();
      processesStore.reset();
    };
  });

  return <MemoryRouter>{children}</MemoryRouter>;
};

describe('PanelHeader', () => {
  beforeEach(async () => {
    processInstanceMigrationStore.setCurrentStep('elementMapping');
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
  });

  it('should render process name and id', async () => {
    const queryString =
      '?active=true&incidents=true&process=demoProcess&version=3';

    const originalWindow = {...window};

    const locationSpy = jest.spyOn(window, 'location', 'get');

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

    processesStore.fetchProcesses();
    render(<Header />, {wrapper: Wrapper});

    expect(await screen.findByText('New demo process')).toBeInTheDocument();
    expect(screen.getByText('demoProcess')).toBeInTheDocument();

    locationSpy.mockRestore();
  });

  it('should render current step and update step details on change', async () => {
    render(<Header />, {wrapper: Wrapper});

    expect(
      screen.getByText('Migration Step 1 - Mapping elements'),
    ).toBeInTheDocument();

    await act(() => {
      processInstanceMigrationStore.setCurrentStep('summary');
    });

    await expect(
      screen.getByText('Migration Step 2 - Confirm'),
    ).toBeInTheDocument();
  });
});
