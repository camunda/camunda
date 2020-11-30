/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';

import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {instancesStore} from 'modules/stores/instances';
import {filtersStore} from 'modules/stores/filters';

import ListFooter from './index';
import {instanceSelectionStore} from 'modules/stores/instanceSelection';

const DROPDOWN_REGEX = /^Apply Operation on \d+ Instance[s]?...$/;
const COPYRIGHT_REGEX = /^© Camunda Services GmbH \d{4}. All rights reserved./;

jest.mock('./CreateOperationDropdown', () => ({label}: any) => (
  <button>{label}</button>
));

const defaultProps = {
  onFirstElementChange: jest.fn(),
  hasContent: true,
};

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

describe('ListFooter', () => {
  beforeAll(() => {
    filtersStore.setEntriesPerPage(10);
  });
  afterAll(() => {
    filtersStore.reset();
  });
  afterEach(() => {
    instanceSelectionStore.reset();
  });
  it('should show pagination, copyright, no dropdown', () => {
    instancesStore.setInstances({
      filteredInstancesCount: 11,
      workflowInstances: mockInstances,
    });

    render(<ListFooter {...defaultProps} />, {wrapper: ThemeProvider});

    const pageOneButton = screen.getByText(/^1$/i);
    const pageTwoButton = screen.getByText(/^2$/i);
    expect(pageOneButton).toBeInTheDocument();
    expect(pageTwoButton).toBeInTheDocument();

    const copyrightText = screen.getByText(COPYRIGHT_REGEX);
    expect(copyrightText).toBeInTheDocument();

    const dropdownButton = screen.queryByText(DROPDOWN_REGEX);
    expect(dropdownButton).toBeNull();
  });

  it('should show copyright, no dropdown, no pagination', () => {
    instancesStore.setInstances({
      filteredInstancesCount: 9,
      workflowInstances: mockInstances,
    });
    render(<ListFooter {...defaultProps} />, {wrapper: ThemeProvider});

    expect(screen.queryByText(/^1$/i)).toBeNull();
    expect(screen.queryByText(/^2$/i)).toBeNull();

    const copyrightText = screen.getByText(COPYRIGHT_REGEX);
    expect(copyrightText).toBeInTheDocument();

    const dropdownButton = screen.queryByText(DROPDOWN_REGEX);
    expect(dropdownButton).toBeNull();
  });

  it('should show Dropdown when there is selection', () => {
    instancesStore.setInstances({
      filteredInstancesCount: 9,
      workflowInstances: mockInstances,
    });
    render(<ListFooter {...defaultProps} />, {wrapper: ThemeProvider});
    instanceSelectionStore.selectInstance('1');
    instanceSelectionStore.selectInstance('2');
    const dropdownButton = screen.getByText(
      'Apply Operation on 2 Instances...'
    );
    expect(dropdownButton).toBeInTheDocument();

    const copyrightText = screen.getByText(COPYRIGHT_REGEX);
    expect(copyrightText).toBeInTheDocument();
  });

  it('should not show the pagination buttons when there is no content', () => {
    instancesStore.setInstances({
      filteredInstancesCount: 11,
      workflowInstances: mockInstances,
    });
    render(<ListFooter {...defaultProps} hasContent={false} />, {
      wrapper: ThemeProvider,
    });

    const pageOneButton = screen.queryByText(/^1$/i);
    const pageTwoButton = screen.queryByText(/^2$/i);
    expect(pageOneButton).toBeNull();
    expect(pageTwoButton).toBeNull();

    const dropdownButton = screen.queryByText(DROPDOWN_REGEX);
    expect(dropdownButton).toBeNull();

    const copyrightText = screen.getByText(COPYRIGHT_REGEX);
    expect(copyrightText).toBeInTheDocument();
  });
});
