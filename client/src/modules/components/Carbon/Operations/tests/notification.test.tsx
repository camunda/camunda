/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, within} from 'modules/testing-library';
import {mockApplyOperation} from 'modules/mocks/api/processInstances/operations';
import {createBatchOperation} from 'modules/testUtils';
import {Operations} from '../index';
import {INSTANCE, Wrapper} from './mocks';

const mockDisplayNotification = jest.fn();
jest.mock('modules/notifications', () => ({
  useNotifications: () => ({
    displayNotification: mockDisplayNotification,
  }),
}));

describe('Operations - Notification', () => {
  it('should not display notification and redirect if delete operation is performed on instances page', async () => {
    const {user} = render(
      <Operations
        instance={{
          ...INSTANCE,
          state: 'COMPLETED',
        }}
        onError={() => {}}
      />,
      {
        wrapper: Wrapper,
      }
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      /^\/carbon\/processes$/
    );
    await user.click(screen.getByRole('button', {name: /Delete Instance/}));
    expect(screen.getByText(/About to delete Instance/)).toBeInTheDocument();

    mockApplyOperation().withSuccess(createBatchOperation());

    const confirmDeletionModal = screen.getByTestId('confirm-deletion-modal');

    const withinConfirmDeletionModal = within(confirmDeletionModal);

    await user.click(
      withinConfirmDeletionModal.getByRole('button', {name: /delete/i})
    );

    expect(
      screen.queryByText(/About to delete Instance/)
    ).not.toBeInTheDocument();

    expect(mockDisplayNotification).not.toHaveBeenCalled();
    expect(screen.getByTestId('pathname')).toHaveTextContent(
      /^\/carbon\/processes$/
    );
  });
});
