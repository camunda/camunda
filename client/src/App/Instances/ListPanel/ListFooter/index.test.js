/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';

import {instances} from 'modules/stores/instances';
import {filters} from 'modules/stores/filters';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import ListFooter from './index';
import {instanceSelection} from 'modules/stores/instanceSelection';

const DROPDOWN_REGEX = /^Apply Operation on \d+ Instance[s]?...$/;
const COPYRIGHT_REGEX = /^© Camunda Services GmbH \d{4}. All rights reserved./;

jest.mock('./CreateOperationDropdown', () => ({label}) => (
  <button>{label}</button>
));

const defaultProps = {
  onFirstElementChange: jest.fn(),
  hasContent: true,
};

describe('ListFooter', () => {
  beforeAll(() => {
    filters.setEntriesPerPage(10);
  });
  afterAll(() => {
    filters.reset();
  });
  afterEach(() => {
    instanceSelection.reset();
  });
  it('should show pagination, copyright, no dropdown', () => {
    instances.setInstances({filteredInstancesCount: 11});

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
    instances.setInstances({filteredInstancesCount: 9});
    render(<ListFooter {...defaultProps} />, {wrapper: ThemeProvider});

    expect(screen.queryByText(/^1$/i)).toBeNull();
    expect(screen.queryByText(/^2$/i)).toBeNull();

    const copyrightText = screen.getByText(COPYRIGHT_REGEX);
    expect(copyrightText).toBeInTheDocument();

    const dropdownButton = screen.queryByText(DROPDOWN_REGEX);
    expect(dropdownButton).toBeNull();
  });

  it('should show Dropdown when there is selection', () => {
    instances.setInstances({filteredInstancesCount: 9});
    render(<ListFooter {...defaultProps} />, {wrapper: ThemeProvider});
    instanceSelection.selectInstance('1');
    instanceSelection.selectInstance('2');
    const dropdownButton = screen.getByText(
      'Apply Operation on 2 Instances...'
    );
    expect(dropdownButton).toBeInTheDocument();

    const copyrightText = screen.getByText(COPYRIGHT_REGEX);
    expect(copyrightText).toBeInTheDocument();
  });

  it('should not show the pagination buttons when there is no content', () => {
    instances.setInstances({filteredInstancesCount: 11});
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
