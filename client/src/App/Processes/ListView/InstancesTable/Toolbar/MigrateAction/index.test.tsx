/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect} from 'react';
import {observer} from 'mobx-react';
import {MemoryRouter} from 'react-router-dom';
import {act, render, screen, waitFor} from '@testing-library/react';
import {Paths} from 'modules/Routes';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {processInstancesStore} from 'modules/stores/processInstances';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockProcessInstances} from 'modules/testUtils';
import {MigrateAction} from '.';

const fetchProcessInstances = async () => {
  await act(async () => {
    await processInstancesStore.fetchInstances({
      fetchType: 'initial',
      payload: {query: {}},
    });
  });

  await waitFor(() => processInstancesStore.state.status === 'fetched');
};

const getProcessInstance = (state: ProcessInstanceEntity['state']) => {
  const instance = mockProcessInstances.processInstances.find(
    (instance) => instance.state === state,
  );
  if (instance === undefined) {
    throw new Error(
      `please make sure there is a ${state} instance mockProcessInstances.processInstances`,
    );
  }

  return instance;
};

function getWrapper(initialPath: string = Paths.processes()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = observer(
    ({children}) => {
      useEffect(() => {
        return () => {
          processInstancesSelectionStore.reset();
          processInstancesStore.reset();
        };
      }, []);
      return (
        <MemoryRouter initialEntries={[initialPath]}>{children}</MemoryRouter>
      );
    },
  );

  return Wrapper;
}

describe('<MigrateAction />', () => {
  it('should disable migrate button, when no process version is selected', () => {
    render(<MigrateAction />, {wrapper: getWrapper()});

    expect(screen.getByRole('button', {name: /migrate/i})).toBeDisabled();
  });

  it('should disable migrate button, when no active or incident instances are selected', () => {
    render(<MigrateAction />, {
      wrapper: getWrapper(
        `/processes?process=eventBasedGatewayProcess&version=1`,
      ),
    });

    expect(screen.getByRole('button', {name: /migrate/i})).toBeDisabled();
  });

  it('should enable migrate button, when active or incident instances are selected', async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);

    render(<MigrateAction />, {
      wrapper: getWrapper(
        `/processes?process=eventBasedGatewayProcess&version=1`,
      ),
    });

    await fetchProcessInstances();

    const instance = getProcessInstance('ACTIVE');

    expect(screen.getByRole('button', {name: /migrate/i})).toBeDisabled();

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(instance.id);
    });

    expect(screen.getByRole('button', {name: /migrate/i})).toBeEnabled();
  });

  it('should disable migrate button, when only finished instances are selected', async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);

    render(<MigrateAction />, {
      wrapper: getWrapper(
        `/processes?process=eventBasedGatewayProcess&version=1`,
      ),
    });

    await fetchProcessInstances();

    const instance = getProcessInstance('CANCELED');

    act(() => {
      processInstancesSelectionStore.selectProcessInstance(instance.id);
    });

    expect(screen.getByRole('button', {name: /migrate/i})).toBeDisabled();
  });

  it('should enable migrate button, when all instances are selected', async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);

    render(<MigrateAction />, {
      wrapper: getWrapper(
        `/processes?process=eventBasedGatewayProcess&version=1`,
      ),
    });

    expect(screen.getByRole('button', {name: /migrate/i})).toBeDisabled();

    await fetchProcessInstances();

    act(() => {
      processInstancesSelectionStore.selectAllProcessInstances();
    });

    expect(screen.getByRole('button', {name: /migrate/i})).toBeEnabled();
  });
});
