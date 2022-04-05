/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, waitFor} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {DeleteOperationModal} from './index';
import {mockProps} from './index.setup';
import userEvent from '@testing-library/user-event';
describe('ConfirmOperationModal', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should render', () => {
    render(<DeleteOperationModal {...mockProps} />, {wrapper: ThemeProvider});
    expect(
      screen.getByText(`About to delete Instance ${mockProps.instanceId}.`)
    ).toBeInTheDocument();
    expect(screen.getByText('Click "Delete" to proceed.')).toBeInTheDocument();
    expect(screen.getByTestId('delete-button')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Cancel'})).toBeInTheDocument();
    expect(screen.getByTestId('cross-button')).toBeInTheDocument();
  });

  it('should call delete function on delete button click', async () => {
    render(<DeleteOperationModal {...mockProps} />, {wrapper: ThemeProvider});
    userEvent.click(screen.getByTestId('delete-button'));
    await waitFor(() =>
      expect(mockProps.onDeleteClick).toHaveBeenCalledTimes(1)
    );
  });

  it('should call close modal function on cancel button click', () => {
    render(<DeleteOperationModal {...mockProps} />, {wrapper: ThemeProvider});
    userEvent.click(screen.getByRole('button', {name: 'Cancel'}));
    expect(mockProps.onModalClose).toHaveBeenCalledTimes(1);
  });

  it('should call close modal function on close button click', () => {
    render(<DeleteOperationModal {...mockProps} />, {wrapper: ThemeProvider});
    userEvent.click(screen.getByTestId('cross-button'));
    expect(mockProps.onModalClose).toHaveBeenCalledTimes(1);
  });
});
