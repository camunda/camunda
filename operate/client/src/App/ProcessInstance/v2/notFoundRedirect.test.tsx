/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {ProcessInstance} from '.';
import {getWrapper, mockRequests} from './mocks';
import {Paths} from 'modules/Routes';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {render, screen, waitFor} from 'modules/testing-library';
import {notificationsStore} from 'modules/stores/notifications';

const handleRefetchSpy = jest.spyOn(
  processInstanceDetailsStore,
  'handleRefetch',
);

jest.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: jest.fn(() => () => {}),
  },
}));

describe('Redirect to process instances page', () => {
  beforeEach(() => {
    mockRequests();
  });

  it('should poll 3 times for not found instance, then redirect to instances page and display notification', async () => {
    jest.useFakeTimers();

    mockFetchProcessDefinitionXml().withServerError();
    mockFetchProcessInstance().withServerError(404);

    render(<ProcessInstance />, {
      wrapper: getWrapper({
        initialPath: Paths.processInstance('123'),
      }),
    });

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('instance-history-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('variables-skeleton')).toBeInTheDocument();

    mockFetchProcessInstance().withServerError(404);
    jest.runOnlyPendingTimers();
    await waitFor(() =>
      expect(processInstanceDetailsStore.state.status).toBe('fetching'),
    );

    expect(handleRefetchSpy).toHaveBeenCalledTimes(1);

    mockFetchProcessInstance().withServerError(404);
    jest.runOnlyPendingTimers();
    await waitFor(() => expect(handleRefetchSpy).toHaveBeenCalledTimes(2));

    mockFetchProcessInstance().withServerError(404);
    jest.runOnlyPendingTimers();
    await waitFor(() => expect(handleRefetchSpy).toHaveBeenCalledTimes(3));

    await waitFor(() => {
      expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/);
      expect(screen.getByTestId('search')).toHaveTextContent(
        /^\?active=true&incidents=true$/,
      );
    });

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'error',
      title: 'Instance 123 could not be found',
      isDismissable: true,
    });

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});
