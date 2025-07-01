/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {screen, waitFor} from '@testing-library/react';
import {Cancel} from './Cancel';
import {render} from 'modules/testing-library';
import {getWrapper} from './mocks';
import {mockFetchCallHierarchy} from 'modules/mocks/api/v2/processInstances/fetchCallHierarchy';
import {mockCancelProcessInstance} from 'modules/mocks/api/v2/processInstances/cancelProcessInstance';
import {notificationsStore} from 'modules/stores/notifications';

jest.mock('modules/stores/notifications', () => {
  return {
    notificationsStore: {
      displayNotification: jest.fn(),
    },
  };
});

describe('Cancel component', () => {
  beforeEach(() => {
    mockFetchCallHierarchy().withSuccess([]);
  });

  it('should show notification on server error', async () => {
    mockCancelProcessInstance().withServerError();

    const {user} = render(<Cancel processInstanceKey="809213809132809" />, {
      wrapper: getWrapper(),
    });

    await user.click(screen.getByRole('button', {name: /cancel instance/i}));
    await user.click(screen.getByRole('button', {name: /apply/i}));

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'error',
        title: 'Failed to cancel process instance',
        isDismissable: true,
        subtitle: 'Internal Server Error',
      }),
    );
  });
});
