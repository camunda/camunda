/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {createMockDataManager} from 'modules/testHelpers/dataManager';
import {createIncident} from 'modules/testUtils';
import {LOADING_STATE} from 'modules/constants';
import IncidentOperation from './index';
import {render, screen, fireEvent} from '@testing-library/react';

jest.mock('modules/DataManager/core');
jest.mock('modules/utils/bpmn');

// mocking api

const mockProps = {
  incident: createIncident(),
  onButtonClick: jest.fn(),
  instanceId: 'instance_1',
  showSpinner: false,
};

describe('IncidentOperation', () => {
  let dataManager;

  beforeEach(() => {
    dataManager = createMockDataManager();
  });

  it('should not render a spinner', () => {
    render(
      <IncidentOperation.WrappedComponent
        {...mockProps}
        dataManager={dataManager}
      />
    );
    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();
  });
  it('should render a spinner if it is forced', () => {
    render(
      <IncidentOperation.WrappedComponent
        {...mockProps}
        dataManager={dataManager}
        showSpinner={true}
      />
    );
    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
  });

  it('should render a spinner when instance operation is published', () => {
    render(
      <IncidentOperation.WrappedComponent
        incident={createIncident()}
        instanceId={'instance_1'}
        dataManager={dataManager}
      />
    );
    var subscriptions = dataManager.subscriptions();
    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    dataManager.publish({
      subscription: subscriptions['OPERATION_APPLIED_INSTANCE_instance_1'],
      state: LOADING_STATE.LOADING,
    });

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
  });

  it('should render a spinner when retry button is clicked', () => {
    render(
      <IncidentOperation.WrappedComponent
        incident={createIncident()}
        instanceId={'instance_1'}
        dataManager={dataManager}
      />
    );
    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId('retry-incident'));

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
  });
});
