/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {ConfirmOperationModal} from './index';
import userEvent from '@testing-library/user-event';

const mockProps = {
  onModalClose: jest.fn(),
  onApplyClick: jest.fn(),
  onCancelClick: jest.fn(),
  isVisible: true,
  bodyText: 'My Content',
};

describe('ConfirmOperationModal', () => {
  it('should render', () => {
    render(<ConfirmOperationModal {...mockProps} />, {wrapper: ThemeProvider});
    expect(screen.getByText(mockProps.bodyText)).toBeInTheDocument();
    expect(screen.getByText('Click "Apply" to proceed.')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Apply'})).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Cancel'})).toBeInTheDocument();
    expect(screen.getByTestId('cross-button')).toBeInTheDocument();
  });

  it('should call apply function on apply button click', () => {
    render(<ConfirmOperationModal {...mockProps} />, {wrapper: ThemeProvider});
    userEvent.click(screen.getByRole('button', {name: 'Apply'}));
    expect(mockProps.onApplyClick).toHaveBeenCalledTimes(1);
  });

  it('should call cancel function on cancel button click', () => {
    render(<ConfirmOperationModal {...mockProps} />, {wrapper: ThemeProvider});
    userEvent.click(screen.getByRole('button', {name: 'Cancel'}));
    expect(mockProps.onCancelClick).toHaveBeenCalledTimes(1);
  });

  it('should call close modal function on close button click', () => {
    render(<ConfirmOperationModal {...mockProps} />, {wrapper: ThemeProvider});
    userEvent.click(screen.getByTestId('cross-button'));
    expect(mockProps.onModalClose).toHaveBeenCalledTimes(1);
  });
});
