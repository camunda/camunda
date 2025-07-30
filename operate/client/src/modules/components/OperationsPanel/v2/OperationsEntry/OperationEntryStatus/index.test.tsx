/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from '@testing-library/react';
import OperationsEntryStatus from './index';

describe('OperationsEntryStatus', () => {
  it('should render instance status count when there is one instance with success status', () => {
    render(
      <OperationsEntryStatus
        isTypeDeleteProcessOrDecision={false}
        label={'Edit'}
        failedOperationsCount={0}
        completedOperationsCount={1}
      />,
    );

    expect(screen.getByText('1 success')).toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(fail)/)).not.toBeInTheDocument();
  });

  it('should render instance status count when there is one instance with fail status', () => {
    render(
      <OperationsEntryStatus
        isTypeDeleteProcessOrDecision={false}
        label={'Edit'}
        failedOperationsCount={1}
        completedOperationsCount={0}
      />,
    );

    expect(screen.getByText('1 fail')).toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(success)/)).not.toBeInTheDocument();
  });

  it('should render only success instance status count when all operations have been successful', () => {
    render(
      <OperationsEntryStatus
        isTypeDeleteProcessOrDecision={false}
        label={'Edit'}
        failedOperationsCount={0}
        completedOperationsCount={3}
      />,
    );

    expect(screen.getByText('3 success')).toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(fail)/)).not.toBeInTheDocument();
  });

  it('should render only failed instance status count when all operations have failed', () => {
    render(
      <OperationsEntryStatus
        isTypeDeleteProcessOrDecision={false}
        label={'Edit'}
        failedOperationsCount={3}
        completedOperationsCount={0}
      />,
    );

    expect(screen.getByText('3 fail')).toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(success)/)).not.toBeInTheDocument();
  });

  it('should render success and fail instance status count when there is a mix of failed and successful operations', () => {
    render(
      <OperationsEntryStatus
        isTypeDeleteProcessOrDecision={false}
        label={'Edit'}
        failedOperationsCount={3}
        completedOperationsCount={3}
      />,
    );

    expect(screen.getByText('3 success')).toBeInTheDocument();
    expect(screen.getByText('3 fail')).toBeInTheDocument();
  });

  it('should render delete process or decision definition instance status count when all operations have been successful', () => {
    render(
      <OperationsEntryStatus
        isTypeDeleteProcessOrDecision={true}
        label={'Delete'}
        failedOperationsCount={0}
        completedOperationsCount={3}
      />,
    );

    expect(screen.getByText('3 instances deleted')).toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(fail)/)).not.toBeInTheDocument();
  });

  it('should render delete process or decision definition instance status count when all operations have failed', () => {
    render(
      <OperationsEntryStatus
        isTypeDeleteProcessOrDecision={true}
        label={'Delete'}
        failedOperationsCount={3}
        completedOperationsCount={0}
      />,
    );

    expect(screen.getByText('3 fail')).toBeInTheDocument();
    expect(
      screen.queryByText(/\d+\s(instance(s)? deleted)/),
    ).not.toBeInTheDocument();
  });

  it('should render delete process or decision definition instance status count when there is a mix of failed and successful operations', () => {
    render(
      <OperationsEntryStatus
        isTypeDeleteProcessOrDecision={true}
        label={'Delete'}
        failedOperationsCount={3}
        completedOperationsCount={3}
      />,
    );

    expect(screen.getByText('3 instances deleted')).toBeInTheDocument();
    expect(screen.getByText('3 fail')).toBeInTheDocument();
  });

  it('should not render instances status count for delete instance operation success', () => {
    render(
      <OperationsEntryStatus
        isTypeDeleteProcessOrDecision={false}
        label={'Delete'}
        failedOperationsCount={0}
        completedOperationsCount={1}
      />,
    );

    expect(
      screen.queryByText(/\d+\s(instance(s)? deleted)/),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(fail)/)).not.toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(success)/)).not.toBeInTheDocument();
  });

  it('should render instances status count for delete instance operation fail', () => {
    render(
      <OperationsEntryStatus
        isTypeDeleteProcessOrDecision={false}
        label={'Delete'}
        failedOperationsCount={1}
        completedOperationsCount={0}
      />,
    );

    expect(screen.getByText('1 fail')).toBeInTheDocument();
    expect(
      screen.queryByText(/\d+\s(instance(s)? deleted)/),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/\d+\s(success)/)).not.toBeInTheDocument();
  });
});
