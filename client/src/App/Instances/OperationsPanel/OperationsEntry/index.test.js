/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen, fireEvent} from '@testing-library/react';

import {OPERATIONS, mockProps} from './index.setup';
import OperationsEntry from './index';

describe('OperationsEntry', () => {
  it('should render retry operation', () => {
    // when
    render(
      <OperationsEntry
        {...mockProps}
        batchOperation={{
          ...OPERATIONS.RETRY,
          instancesCount: 1,
        }}
      />
    );

    // then
    expect(screen.getByText(OPERATIONS.RETRY.id)).toBeInTheDocument();
    expect(screen.getByText('Retry')).toBeInTheDocument();
    expect(screen.getByTestId('operation-icon-Retry')).toBeInTheDocument();
    expect(screen.getAllByTestId(/operation-icon/)).toHaveLength(1);
  });

  it('should render cancel operation', () => {
    // when
    render(
      <OperationsEntry
        {...mockProps}
        batchOperation={{
          ...OPERATIONS.CANCEL,
          instancesCount: 1,
        }}
      />
    );

    // then
    expect(screen.getByText('12 Dec 2018 00:00:00')).toBeInTheDocument();
    expect(screen.getByText(OPERATIONS.CANCEL.id)).toBeInTheDocument();
    expect(screen.getByText('Cancel')).toBeInTheDocument();
    expect(screen.getByTestId('operation-icon-Cancel')).toBeInTheDocument();
    expect(screen.getAllByTestId(/operation-icon/)).toHaveLength(1);
  });

  it('should render edit operation', () => {
    // when
    render(
      <OperationsEntry
        {...mockProps}
        batchOperation={{
          ...OPERATIONS.EDIT,
          instancesCount: 1,
        }}
      />
    );

    // then
    expect(screen.getByText('12 Dec 2018 00:00:00')).toBeInTheDocument();
    expect(screen.getByText(OPERATIONS.EDIT.id)).toBeInTheDocument();
    expect(screen.getByText('Edit')).toBeInTheDocument();
    expect(screen.getByTestId('operation-icon-Edit')).toBeInTheDocument();
    expect(screen.getAllByTestId(/operation-icon/)).toHaveLength(1);
  });

  it('should render instances count with when there is one instance', () => {
    // when
    render(
      <OperationsEntry
        {...mockProps}
        batchOperation={{
          ...OPERATIONS.EDIT,
          instancesCount: 1,
        }}
      />
    );

    // then
    expect(screen.getByText('1 Instance')).toBeInTheDocument();
  });

  it('should render instances count with when there is more than one instance', () => {
    // when
    render(
      <OperationsEntry
        {...mockProps}
        batchOperation={{
          ...OPERATIONS.EDIT,
          instancesCount: 3,
        }}
      />
    );

    // then
    expect(screen.getByText('3 Instances')).toBeInTheDocument();
  });

  it('should be able to handle instance click', () => {
    // given
    render(
      <OperationsEntry
        {...mockProps}
        batchOperation={{
          ...OPERATIONS.EDIT,
          instancesCount: 3,
        }}
      />
    );

    // when
    fireEvent.click(screen.getByText('3 Instances'));

    // then
    expect(mockProps.onInstancesClick).toHaveBeenCalledWith(
      'df325d44-6a4c-4428-b017-24f923f1d052'
    );
  });
});
