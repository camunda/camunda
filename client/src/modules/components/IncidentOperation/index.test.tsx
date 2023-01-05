/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {createBatchOperation, createIncident} from 'modules/testUtils';
import {IncidentOperation} from './index';
import {
  render,
  screen,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {operationsStore} from 'modules/stores/operations';
import {mockProps} from './index.setup';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {NotificationProvider} from 'modules/notifications';
import {mockApplyOperation} from 'modules/mocks/api/processInstances/operations';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <ThemeProvider>
      <NotificationProvider>{children}</NotificationProvider>
    </ThemeProvider>
  );
};

describe('IncidentOperation', () => {
  beforeAll(() => {
    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = false;
  });

  afterAll(() => {
    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = true;
  });

  afterEach(() => {
    operationsStore.reset();
  });

  it('should not render a spinner and disable button', () => {
    render(<IncidentOperation {...mockProps} />, {wrapper: Wrapper});
    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Retry Incident'})).toBeEnabled();
  });

  it('should render a spinner and disable button if it is forced', () => {
    render(<IncidentOperation {...mockProps} showSpinner={true} />, {
      wrapper: ThemeProvider,
    });
    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Retry Incident'})).toBeDisabled();
  });

  it('should render a spinner and disable retry button when instance operation is applied', async () => {
    mockApplyOperation().withSuccess(createBatchOperation());

    const {user} = render(
      <IncidentOperation
        incident={createIncident()}
        instanceId={'instance_1'}
      />,
      {
        wrapper: Wrapper,
      }
    );
    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Retry Incident'}));

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Retry Incident'})).toBeDisabled();
  });

  it('should remove spinner and enable button when a server error occurs on an operation', async () => {
    mockApplyOperation().withServerError();

    const {user} = render(
      <IncidentOperation incident={createIncident()} instanceId="instance_1" />,
      {
        wrapper: Wrapper,
      }
    );
    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Retry Incident'}));

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Retry Incident'})).toBeDisabled();

    await waitForElementToBeRemoved(screen.getByTestId('operation-spinner'));

    expect(screen.getByRole('button', {name: 'Retry Incident'})).toBeEnabled();
  });

  it('should remove spinner and enable button when a network error occurs on an operation', async () => {
    mockApplyOperation().withNetworkError();

    const {user} = render(
      <IncidentOperation incident={createIncident()} instanceId="instance_1" />,
      {
        wrapper: Wrapper,
      }
    );
    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Retry Incident'}));

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Retry Incident'})).toBeDisabled();

    await waitForElementToBeRemoved(screen.getByTestId('operation-spinner'));

    expect(screen.getByRole('button', {name: 'Retry Incident'})).toBeEnabled();
  });
});
