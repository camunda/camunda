/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {ErrorMessageModal} from './index';
import {mockProps, mockHiddenModalProps} from './index.setup';

import {render, screen, fireEvent} from '@testing-library/react';

it('should render modal', () => {
  render(<ErrorMessageModal {...mockProps} />);
  expect(screen.getByText('modal title')).toBeInTheDocument();
  expect(screen.getByText('some modal content')).toBeInTheDocument();
});

it('should hide modal', () => {
  render(<ErrorMessageModal {...mockHiddenModalProps} />);

  expect(screen.queryByText('modal title')).not.toBeInTheDocument();
  expect(screen.queryByText('some modal content')).not.toBeInTheDocument();
});

it('should close modal on modal close click', () => {
  render(<ErrorMessageModal {...mockProps} />);

  fireEvent.click(screen.getByText('Close'));
  expect(mockProps.toggleModal).toBeCalled();
});

it('should close modal on close button click', () => {
  render(<ErrorMessageModal {...mockProps} />);

  fireEvent.click(screen.getByTestId('cross-button'));
  expect(mockProps.toggleModal).toBeCalled();
});
