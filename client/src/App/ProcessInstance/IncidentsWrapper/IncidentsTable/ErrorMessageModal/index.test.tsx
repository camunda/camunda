/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen} from 'modules/testing-library';
import {ErrorMessageModal} from './index';
import {mockProps, mockHiddenModalProps} from './index.setup';
import {ThemeProvider} from 'modules/theme/ThemeProvider';

it('should render modal', () => {
  render(<ErrorMessageModal {...mockProps} />, {wrapper: ThemeProvider});
  expect(screen.getByText('modal title')).toBeInTheDocument();
  expect(screen.getByText('some modal content')).toBeInTheDocument();
});

it('should hide modal', () => {
  render(<ErrorMessageModal {...mockHiddenModalProps} />, {
    wrapper: ThemeProvider,
  });

  expect(screen.queryByText('modal title')).not.toBeInTheDocument();
  expect(screen.queryByText('some modal content')).not.toBeInTheDocument();
});

it('should close modal on modal close click', async () => {
  const {user} = render(<ErrorMessageModal {...mockProps} />, {
    wrapper: ThemeProvider,
  });

  await user.click(screen.getByText('Close'));
  expect(mockProps.onModalClose).toBeCalled();
});

it('should close modal on close button click', async () => {
  const {user} = render(<ErrorMessageModal {...mockProps} />, {
    wrapper: ThemeProvider,
  });

  await user.click(screen.getByTestId('cross-button'));
  expect(mockProps.onModalClose).toBeCalled();
});
