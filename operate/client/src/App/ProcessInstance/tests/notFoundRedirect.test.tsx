/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ProcessInstance} from '../index';
import {getWrapper} from './mocks';
import {Paths} from 'modules/Routes';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {render, screen, waitFor} from 'modules/testing-library';
import {notificationsStore} from 'modules/stores/notifications';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchCallHierarchy} from 'modules/mocks/api/v2/processInstances/fetchCallHierarchy';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

describe('Redirect to process instances page', () => {
  it('should redirect to instances page and display notification if instance is not found (404)', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    mockFetchProcessDefinitionXml().withServerError();
    mockFetchProcessInstance().withServerError(404);
    mockFetchCallHierarchy().withServerError(404);

    render(<ProcessInstance />, {
      wrapper: getWrapper({
        initialPath: Paths.processInstance('123'),
      }),
    });

    await waitFor(() => {
      expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/);
    });
    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?active=true&incidents=true$/,
    );
    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'error',
      title: 'Instance 123 could not be found',
      isDismissable: true,
    });

    vi.clearAllTimers();
    vi.useRealTimers();
  });
});
