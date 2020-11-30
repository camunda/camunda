/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen, fireEvent} from '@testing-library/react';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {filtersStore} from 'modules/stores/filters';
import {instancesStore} from 'modules/stores/instances';
import {Paginator} from './index';

const mockInstances = [
  {
    id: '2251799813685625',
    workflowId: '2251799813685623',
    workflowName: 'Without Incidents Process',
    workflowVersion: 1,
    startDate: '2020-11-19T08:14:05.406+0000',
    endDate: null,
    state: 'ACTIVE',
    bpmnProcessId: 'withoutIncidentsProcess',
    hasActiveOperation: false,
    operations: [],
  } as const,
  {
    id: '2251799813685627',
    workflowId: '2251799813685623',
    workflowName: 'Without Incidents Process',
    workflowVersion: 1,
    startDate: '2020-11-19T08:14:05.490+0000',
    endDate: null,
    state: 'ACTIVE',
    bpmnProcessId: 'withoutIncidentsProcess',
    hasActiveOperation: false,
    operations: [],
  } as const,
];

describe('Paginator', () => {
  afterEach(() => {
    filtersStore.reset();
    instancesStore.reset();
  });

  it('should show the first five pages when on first page', () => {
    filtersStore.setEntriesPerPage(10);
    filtersStore.setPage(1);
    instancesStore.setInstances({
      filteredInstancesCount: 150,
      workflowInstances: mockInstances,
    });

    render(<Paginator maxPage={15} />, {wrapper: ThemeProvider});

    [1, 2, 3, 4, 5, 15].forEach((pageNumber) => {
      expect(
        screen.getByRole('button', {name: `Page ${pageNumber}`})
      ).toBeInTheDocument();
    });

    expect(screen.getByText('…')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Page 6'})
    ).not.toBeInTheDocument();
  });

  it('should show the last five pages when on last page', () => {
    filtersStore.setEntriesPerPage(10);
    filtersStore.setPage(15);
    instancesStore.setInstances({
      filteredInstancesCount: 150,
      workflowInstances: mockInstances,
    });

    render(<Paginator maxPage={15} />, {wrapper: ThemeProvider});
    [15, 14, 13, 12, 11, 1].forEach((pageNumber) => {
      expect(
        screen.getByRole('button', {name: `Page ${pageNumber}`})
      ).toBeInTheDocument();
    });
    expect(screen.getByText('…')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Page 10'})
    ).not.toBeInTheDocument();
  });

  it('should show ellipsis on both sides in the middle', () => {
    filtersStore.setEntriesPerPage(5);
    filtersStore.setPage(10);
    instancesStore.setInstances({
      filteredInstancesCount: 150,
      workflowInstances: mockInstances,
    });

    render(<Paginator maxPage={20} />, {wrapper: ThemeProvider});
    expect(screen.getAllByText('…').length).toBe(2);
  });

  it('should not show ellipsis when first page is just barely out of range', () => {
    filtersStore.setEntriesPerPage(10);
    filtersStore.setPage(1);
    instancesStore.setInstances({
      filteredInstancesCount: 20,
      workflowInstances: mockInstances,
    });
    render(<Paginator maxPage={2} />, {wrapper: ThemeProvider});
    expect(screen.queryByText('…')).not.toBeInTheDocument();
  });

  it('should navigate between pages', () => {
    filtersStore.setEntriesPerPage(10);
    filtersStore.setPage(1);
    instancesStore.setInstances({
      filteredInstancesCount: 30,
      workflowInstances: mockInstances,
    });
    render(<Paginator maxPage={3} />, {wrapper: ThemeProvider});

    expect(screen.getByRole('button', {name: 'First page'})).toBeDisabled();
    expect(screen.getByRole('button', {name: 'Previous page'})).toBeDisabled();
    expect(screen.getByRole('button', {name: 'Last page'})).toBeEnabled();
    expect(screen.getByRole('button', {name: 'Next page'})).toBeEnabled();
    expect(filtersStore.state.page).toBe(1);

    fireEvent.click(screen.getByRole('button', {name: 'Next page'}));
    ['First page', 'Previous page', 'Last page', 'Next page'].forEach((name) =>
      expect(screen.getByRole('button', {name})).toBeEnabled()
    );
    expect(filtersStore.state.page).toBe(2);

    fireEvent.click(screen.getByRole('button', {name: 'Next page'}));
    expect(screen.getByRole('button', {name: 'First page'})).toBeEnabled();
    expect(screen.getByRole('button', {name: 'Previous page'})).toBeEnabled();
    expect(screen.getByRole('button', {name: 'Last page'})).toBeDisabled();
    expect(screen.getByRole('button', {name: 'Next page'})).toBeDisabled();
    expect(filtersStore.state.page).toBe(3);

    fireEvent.click(screen.getByRole('button', {name: 'First page'}));
    expect(screen.getByRole('button', {name: 'First page'})).toBeDisabled();
    expect(screen.getByRole('button', {name: 'Previous page'})).toBeDisabled();
    expect(screen.getByRole('button', {name: 'Last page'})).toBeEnabled();
    expect(screen.getByRole('button', {name: 'Next page'})).toBeEnabled();
    expect(filtersStore.state.page).toBe(1);

    fireEvent.click(screen.getByRole('button', {name: 'Last page'}));
    expect(screen.getByRole('button', {name: 'First page'})).toBeEnabled();
    expect(screen.getByRole('button', {name: 'Previous page'})).toBeEnabled();
    expect(screen.getByRole('button', {name: 'Last page'})).toBeDisabled();
    expect(screen.getByRole('button', {name: 'Next page'})).toBeDisabled();
    expect(filtersStore.state.page).toBe(3);

    fireEvent.click(screen.getByRole('button', {name: 'Previous page'}));
    ['First page', 'Previous page', 'Last page', 'Next page'].forEach((name) =>
      expect(screen.getByRole('button', {name})).toBeEnabled()
    );
    expect(filtersStore.state.page).toBe(2);
  });
});
