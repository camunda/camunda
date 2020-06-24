/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {VariablePanel} from './index';
import {render, screen} from '@testing-library/react';
import {FAILED_PLACEHOLDER, MULTI_SCOPE_PLACEHOLDER} from './constants';
import {flowNodeInstance} from 'modules/stores/flowNodeInstance';
import {variables} from 'modules/stores/variables';
import {currentInstance} from 'modules/stores/currentInstance';
import PropTypes from 'prop-types';
import {MemoryRouter, Route} from 'react-router-dom';

jest.mock('../Variables', () => {
  return {
    __esModule: true,
    default: () => {
      return <div>{'Variables'}</div>;
    },
  };
});

jest.mock('modules/api/instances', () => ({
  fetchVariables: jest.fn().mockImplementation((param) => {
    if (param.instanceId === 'invalid_instance')
      return {error: 'An error occured'};
    else {
      return [];
    }
  }),
}));

const Wrapper = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/instances/1']}>
        <Route path="/instances/:id">{children} </Route>
      </MemoryRouter>
    </ThemeProvider>
  );
};
Wrapper.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node,
  ]),
};

describe('VariablePanel', () => {
  beforeEach(() => {
    currentInstance.setCurrentInstance({
      id: 'instance_id',
      state: 'ACTIVE',
    });
  });
  afterEach(() => {
    flowNodeInstance.reset();
    variables.reset();
  });

  it('should show multiple scope placeholder when multiple nodes are selected', () => {
    flowNodeInstance.setCurrentSelection({flowNodeId: 1, treeRowIds: [1, 2]});
    render(<VariablePanel />, {wrapper: Wrapper});

    expect(screen.getByText(MULTI_SCOPE_PLACEHOLDER)).toBeInTheDocument();
  });

  it('should show failed placeholder when variables could not be fetched', async () => {
    flowNodeInstance.setCurrentSelection({flowNodeId: null, treeRowIds: []});
    render(<VariablePanel />, {wrapper: Wrapper});
    await variables.fetchVariables('invalid_instance');

    expect(screen.getByText(FAILED_PLACEHOLDER)).toBeInTheDocument();
  });

  it('should render variables', async () => {
    render(<VariablePanel />, {wrapper: Wrapper});
    await variables.fetchVariables(1);

    expect(screen.getByText('Variables')).toBeInTheDocument();
  });
});
