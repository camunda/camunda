/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render} from '@testing-library/react';

import InstanceSelectionContext from 'modules/contexts/InstanceSelectionContext';
import {instances} from 'modules/stores/instances';

import ListFooter from './ListFooter';

const DROPDOWN_REGEX = /^Apply Operation on \d+ Instance[s]?...$/;
const COPYRIGHT_REGEX = /^© Camunda Services GmbH \d{4}. All rights reserved./;

jest.mock('./CreateOperationDropdown', () => ({label}) => (
  <button>{label}</button>
));

const defaultContext = {getSelectedCount: () => 0};

const defaultProps = {
  onFirstElementChange: jest.fn(),
  perPage: 10,
  firstElement: 0,
  dataManager: {},
  hasContent: true,
};

describe('ListFooter', () => {
  it('should show pagination, copyright, no dropdown', () => {
    instances.setInstances({filteredInstancesCount: 11});
    const {getByText, queryByText} = render(
      <InstanceSelectionContext.Provider value={defaultContext}>
        <ListFooter {...defaultProps} />
      </InstanceSelectionContext.Provider>
    );

    const pageOneButton = getByText(/^1$/i);
    const pageTwoButton = getByText(/^2$/i);
    expect(pageOneButton).toBeInTheDocument();
    expect(pageTwoButton).toBeInTheDocument();

    const copyrightText = getByText(COPYRIGHT_REGEX);
    expect(copyrightText).toBeInTheDocument();

    const dropdownButton = queryByText(DROPDOWN_REGEX);
    expect(dropdownButton).toBeNull();
  });

  it('should show copyright, no dropdown, no pagination', () => {
    instances.setInstances({filteredInstancesCount: 9});
    const {getByText, queryByText} = render(
      <InstanceSelectionContext.Provider value={defaultContext}>
        <ListFooter {...defaultProps} />
      </InstanceSelectionContext.Provider>
    );

    expect(queryByText(/^1$/i)).toBeNull();
    expect(queryByText(/^2$/i)).toBeNull();

    const copyrightText = getByText(COPYRIGHT_REGEX);
    expect(copyrightText).toBeInTheDocument();

    const dropdownButton = queryByText(DROPDOWN_REGEX);
    expect(dropdownButton).toBeNull();
  });

  it('should show Dropdown when there is selection', () => {
    instances.setInstances({filteredInstancesCount: 9});
    const {getByText} = render(
      <InstanceSelectionContext.Provider
        value={{...defaultContext, getSelectedCount: () => 2}}
      >
        <ListFooter {...defaultProps} />
      </InstanceSelectionContext.Provider>
    );

    const dropdownButton = getByText('Apply Operation on 2 Instances...');
    expect(dropdownButton).toBeInTheDocument();

    const copyrightText = getByText(COPYRIGHT_REGEX);
    expect(copyrightText).toBeInTheDocument();
  });

  it('should not show the pagination buttons when there is no content', () => {
    instances.setInstances({filteredInstancesCount: 11});
    const {queryByText, getByText} = render(
      <InstanceSelectionContext.Provider
        value={{...defaultContext, getSelectedCount: () => 2}}
      >
        <ListFooter {...defaultProps} hasContent={false} />
      </InstanceSelectionContext.Provider>
    );

    const pageOneButton = queryByText(/^1$/i);
    const pageTwoButton = queryByText(/^2$/i);
    expect(pageOneButton).toBeNull();
    expect(pageTwoButton).toBeNull();

    const dropdownButton = queryByText(DROPDOWN_REGEX);
    expect(dropdownButton).toBeNull();

    const copyrightText = getByText(COPYRIGHT_REGEX);
    expect(copyrightText).toBeInTheDocument();
  });
});
