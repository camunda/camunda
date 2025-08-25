/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from '@testing-library/react';
import {OperationEntryStatus} from './index';

describe('OperationEntryStatus', () => {
  it('should render instance status count when there is one instance with success status', () => {
    render(
      <OperationEntryStatus
        type="CANCEL_PROCESS_INSTANCE"
        failedCount={0}
        completedCount={1}
      />,
    );

    expect(screen.getByText('1 instance succeeded')).toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(failed)/)).not.toBeInTheDocument();
  });

  it('should render instance status count when there is one instance with fail status', () => {
    render(
      <OperationEntryStatus
        type="CANCEL_PROCESS_INSTANCE"
        failedCount={1}
        completedCount={0}
      />,
    );

    expect(screen.getByText('1 instance failed')).toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(succeeded)/)).not.toBeInTheDocument();
  });

  it('should render only success instance status count when all operations have been successful', () => {
    render(
      <OperationEntryStatus
        type="CANCEL_PROCESS_INSTANCE"
        failedCount={0}
        completedCount={3}
      />,
    );

    expect(screen.getByText('3 instances succeeded')).toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(failed)/)).not.toBeInTheDocument();
  });

  it('should render only failed instance status count when all operations have failed', () => {
    render(
      <OperationEntryStatus
        type="CANCEL_PROCESS_INSTANCE"
        failedCount={3}
        completedCount={0}
      />,
    );

    expect(screen.getByText('3 instances failed')).toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(succeeded)/)).not.toBeInTheDocument();
  });

  it('should render success and fail instance status count when there is a mix of failed and successful operations', () => {
    render(
      <OperationEntryStatus
        type="CANCEL_PROCESS_INSTANCE"
        failedCount={2}
        completedCount={4}
      />,
    );

    expect(screen.getByText('4 instances succeeded')).toBeInTheDocument();
    expect(screen.getByText('2 failed')).toBeInTheDocument();
  });
});
