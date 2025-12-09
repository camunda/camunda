/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen} from 'modules/testing-library';
import {BatchItemsCount} from './BatchItemsCount';

describe('<BatchItemsCount />', () => {
  it('should render not started state when no operations have started', () => {
    render(
      <BatchItemsCount
        operationsCompletedCount={0}
        operationsFailedCount={0}
        operationsTotalCount={10}
      />,
    );

    expect(screen.getByText('0')).toBeInTheDocument();
    expect(screen.getByLabelText('not started')).toBeInTheDocument();
  });

  it('should filter out items with 0 count', () => {
    render(
      <BatchItemsCount
        operationsCompletedCount={5}
        operationsFailedCount={0}
        operationsTotalCount={10}
      />,
    );

    // Should show successful and pending, but not failed
    expect(screen.getByLabelText('5 successful')).toBeInTheDocument();
    expect(screen.getByLabelText('5 pending')).toBeInTheDocument();
    expect(screen.queryByLabelText(/failed/)).not.toBeInTheDocument();
  });

  it('should display correct counts for successful, failed, and pending operations', () => {
    render(
      <BatchItemsCount
        operationsCompletedCount={15}
        operationsFailedCount={3}
        operationsTotalCount={25}
      />,
    );

    // Should show all three statuses with correct counts
    expect(screen.getByLabelText('15 successful')).toBeInTheDocument();
    expect(screen.getByLabelText('3 failed')).toBeInTheDocument();
    expect(screen.getByLabelText('7 pending')).toBeInTheDocument();

    // Verify the visible text shows formatted numbers
    expect(screen.getByText('15')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('7')).toBeInTheDocument();
  });

  it('should format large numbers using compact notation', () => {
    render(
      <BatchItemsCount
        operationsCompletedCount={1500}
        operationsFailedCount={250}
        operationsTotalCount={2000}
      />,
    );

    expect(screen.getByLabelText('1,500 successful')).toBeInTheDocument();
    expect(screen.getByLabelText('250 failed')).toBeInTheDocument();
    expect(screen.getByLabelText('250 pending')).toBeInTheDocument();

    expect(screen.getByText('1.5K')).toBeInTheDocument();
    expect(screen.getAllByText('250')).toHaveLength(2);
  });

  it('should show only successful operations when all are completed', () => {
    render(
      <BatchItemsCount
        operationsCompletedCount={20}
        operationsFailedCount={0}
        operationsTotalCount={20}
      />,
    );

    expect(screen.getByLabelText('20 successful')).toBeInTheDocument();
    expect(screen.queryByLabelText(/failed/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/pending/)).not.toBeInTheDocument();
  });

  it('should show only failed operations when all have failed', () => {
    render(
      <BatchItemsCount
        operationsCompletedCount={0}
        operationsFailedCount={10}
        operationsTotalCount={10}
      />,
    );

    expect(screen.getByLabelText('10 failed')).toBeInTheDocument();
    expect(screen.queryByLabelText(/successful/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/pending/)).not.toBeInTheDocument();
  });
});
