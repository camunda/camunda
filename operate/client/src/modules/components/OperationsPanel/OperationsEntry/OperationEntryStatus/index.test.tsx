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
        state="COMPLETED"
      />,
    );

    expect(screen.getByText(/1 operation succeeded/i)).toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(failed)/i)).not.toBeInTheDocument();
  });

  it('should render instance status count when there is one instance with fail status', () => {
    render(
      <OperationEntryStatus
        type="CANCEL_PROCESS_INSTANCE"
        failedCount={1}
        completedCount={0}
        state="COMPLETED"
      />,
    );

    expect(screen.getByText(/1 operation failed/i)).toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(succeeded)/i)).not.toBeInTheDocument();
  });

  it('should render only success instance status count when all operations have been successful', () => {
    render(
      <OperationEntryStatus
        type="CANCEL_PROCESS_INSTANCE"
        failedCount={0}
        completedCount={3}
        state="COMPLETED"
      />,
    );

    expect(screen.getByText(/3 operations succeeded/i)).toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(failed)/i)).not.toBeInTheDocument();
  });

  it('should render only failed instance status count when all operations have failed', () => {
    render(
      <OperationEntryStatus
        type="CANCEL_PROCESS_INSTANCE"
        failedCount={3}
        completedCount={0}
        state="COMPLETED"
      />,
    );

    expect(screen.getByText(/3 operations failed/i)).toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(succeeded)/i)).not.toBeInTheDocument();
  });

  it('should render success and fail instance status count when there is a mix of failed and successful operations', () => {
    render(
      <OperationEntryStatus
        type="CANCEL_PROCESS_INSTANCE"
        failedCount={2}
        completedCount={4}
        state="COMPLETED"
      />,
    );

    expect(screen.getByText(/4 operations succeeded/i)).toBeInTheDocument();
    expect(screen.getByText(/2 failed/i)).toBeInTheDocument();
  });

  it('should render partially completed batch operation', () => {
    render(
      <OperationEntryStatus
        type="CANCEL_PROCESS_INSTANCE"
        failedCount={2}
        completedCount={4}
        state="PARTIALLY_COMPLETED"
      />,
    );

    expect(screen.getByText(/4 operations succeeded/i)).toBeInTheDocument();
    expect(screen.getByText(/2 failed/i)).toBeInTheDocument();
  });

  it('should render failed batch operation', () => {
    render(
      <OperationEntryStatus
        type="CANCEL_PROCESS_INSTANCE"
        failedCount={0}
        completedCount={0}
        state="FAILED"
      />,
    );

    expect(
      screen.queryByText(/0 operations succeeded/i),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/0 operations failed/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/0 failed/i)).not.toBeInTheDocument();
    expect(screen.getByText(/failed/i)).toBeInTheDocument();
  });

  it('should render incident resolution specific terms', () => {
    render(
      <OperationEntryStatus
        type="RESOLVE_INCIDENT"
        failedCount={2}
        completedCount={4}
        state="COMPLETED"
      />,
    );

    expect(screen.getByText(/4 retries succeeded/i)).toBeInTheDocument();
    expect(screen.getByText(/2 rejected/i)).toBeInTheDocument();
  });

  it('should render delete process instance operation status', () => {
    render(
      <OperationEntryStatus
        type="DELETE_PROCESS_INSTANCE"
        failedCount={1}
        completedCount={3}
        state="COMPLETED"
      />,
    );

    expect(screen.getByText(/3 operations succeeded/i)).toBeInTheDocument();
    expect(screen.getByText(/1 failed/i)).toBeInTheDocument();
  });

  it('should render delete process definition operation status', () => {
    render(
      <OperationEntryStatus
        type="DELETE_PROCESS_DEFINITION"
        failedCount={0}
        completedCount={2}
        state="COMPLETED"
      />,
    );

    expect(screen.getByText(/2 operations succeeded/i)).toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(failed)/i)).not.toBeInTheDocument();
  });

  it('should render delete decision definition operation status', () => {
    render(
      <OperationEntryStatus
        type="DELETE_DECISION_DEFINITION"
        failedCount={5}
        completedCount={0}
        state="COMPLETED"
      />,
    );

    expect(screen.getByText(/5 operations failed/i)).toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(succeeded)/i)).not.toBeInTheDocument();
  });
});
