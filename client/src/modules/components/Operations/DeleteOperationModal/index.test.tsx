/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {DeleteOperationModal} from './index';
import {mockProps} from './index.setup';

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
    const {user} = render(<DeleteOperationModal {...mockProps} />, {
      wrapper: ThemeProvider,
    });
    await user.click(screen.getByTestId('delete-button'));
    await waitFor(() =>
      expect(mockProps.onDeleteClick).toHaveBeenCalledTimes(1)
    );
  });

  it('should call close modal function on cancel button click', async () => {
    const {user} = render(<DeleteOperationModal {...mockProps} />, {
      wrapper: ThemeProvider,
    });
    await user.click(screen.getByRole('button', {name: 'Cancel'}));
    expect(mockProps.onModalClose).toHaveBeenCalledTimes(1);
  });

  it('should call close modal function on close button click', async () => {
    const {user} = render(<DeleteOperationModal {...mockProps} />, {
      wrapper: ThemeProvider,
    });
    await user.click(screen.getByTestId('cross-button'));
    expect(mockProps.onModalClose).toHaveBeenCalledTimes(1);
  });
});
