/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {
  render,
  screen,
  waitForElementToBeRemoved,
  fireEvent,
} from '@testing-library/react';
import {createMockDataManager} from 'modules/testHelpers/dataManager';
import {DataManagerProvider} from 'modules/DataManager';
import {formatDate} from 'modules/utils/date';
import {getWorkflowName} from 'modules/utils/instance';
import {InstanceHeader} from './index';
import {currentInstance} from 'modules/stores/currentInstance';
import {variables} from 'modules/stores/variables';
import PropTypes from 'prop-types';
import {fetchWorkflowInstance, fetchVariables} from 'modules/api/instances';
import {createInstance} from 'modules/testUtils';

jest.mock('modules/api/instances');

const mockInstanceWithActiveOperation = Object.freeze(createInstance());
const mockInstanceWithoutOperations = Object.freeze({
  ...mockInstanceWithActiveOperation,
  hasActiveOperation: false,
  operations: [],
});

const Wrapper = ({children}) => {
  return <DataManagerProvider>{children}</DataManagerProvider>;
};
Wrapper.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node,
  ]),
};

function resetMocks() {
  variables.reset();
  currentInstance.reset();
  jest.clearAllMocks();
}

describe('InstanceHeader', () => {
  beforeEach(resetMocks);
  afterAll(resetMocks);

  it('should show skeleton before instance data is available', async () => {
    fetchWorkflowInstance.mockImplementationOnce(
      () => mockInstanceWithActiveOperation
    );
    render(<InstanceHeader />, {
      wrapper: Wrapper,
    });

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    currentInstance.init(mockInstanceWithActiveOperation.id);

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );
  });

  it('should render instance data', async () => {
    fetchWorkflowInstance.mockImplementationOnce(
      () => mockInstanceWithActiveOperation
    );
    render(<InstanceHeader />, {
      wrapper: Wrapper,
    });

    currentInstance.init(mockInstanceWithActiveOperation.id);
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );
    const {instance} = currentInstance.state;
    const workflowName = getWorkflowName(instance);
    const instanceState = instance.state;
    const formattedStartDate = formatDate(instance.startDate);
    const formattedEndDate = formatDate(instance.endDate);

    expect(screen.getByText(workflowName)).toBeInTheDocument();
    expect(screen.getByText(instance.id)).toBeInTheDocument();
    expect(
      screen.getByText(`Version ${instance.workflowVersion}`)
    ).toBeInTheDocument();
    expect(screen.getByText(formattedStartDate)).toBeInTheDocument();
    expect(screen.getByText(formattedEndDate)).toBeInTheDocument();
    expect(screen.getByTestId(`${instanceState}-icon`)).toBeInTheDocument();
  });

  it('should show spinner based on instance having active operations', async () => {
    fetchWorkflowInstance
      .mockImplementationOnce(() => mockInstanceWithoutOperations)
      .mockImplementationOnce(() => mockInstanceWithActiveOperation);
    render(<InstanceHeader />, {
      wrapper: Wrapper,
    });

    jest.useFakeTimers();
    currentInstance.init(mockInstanceWithoutOperations.id);
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    jest.advanceTimersByTime(5000);

    expect(await screen.findByTestId('operation-spinner')).toBeInTheDocument();
  });

  it('should show spinner when operation is applied', async () => {
    createMockDataManager();
    fetchWorkflowInstance.mockImplementationOnce(
      () => mockInstanceWithoutOperations
    );
    render(<InstanceHeader />, {
      wrapper: Wrapper,
    });

    currentInstance.init(mockInstanceWithoutOperations.id);
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    fireEvent.click(
      screen.getByRole('button', {name: new RegExp('Cancel Instance')})
    );

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
  });

  it('should show spinner when variables is updated', async () => {
    const mockVariable = {
      name: 'key',
      value: 'value',
      hasActiveOperation: false,
    };
    fetchWorkflowInstance.mockImplementationOnce(
      () => mockInstanceWithoutOperations
    );
    fetchVariables.mockImplementationOnce(() => [mockVariable]);

    render(<InstanceHeader />, {
      wrapper: Wrapper,
    });
    currentInstance.init(mockInstanceWithActiveOperation.id);
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    variables.addVariable(
      mockInstanceWithoutOperations.id,
      mockVariable.name,
      mockVariable.value
    );

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();

    variables.fetchVariables(mockInstanceWithActiveOperation.id);

    await waitForElementToBeRemoved(screen.queryByTestId('operation-spinner'));
  });
});
