/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {
  render,
  screen,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {IncidentOperation} from '.';
import {notificationsStore} from 'modules/stores/notifications';
import {mockResolveIncident} from 'modules/mocks/api/v2/incidents/resolveIncident';
import {tracking} from 'modules/tracking';
import {mockUpdateJob} from 'modules/mocks/api/v2/jobs/updateJob';
import {mockGetIncident} from 'modules/mocks/api/v2/incidents/getIncident';
import {createIncident} from 'modules/testUtils';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      {children}
    </QueryClientProvider>
  );
};

describe('IncidentOperation', () => {
  const retryButton = () =>
    screen.getByRole('button', {
      name: 'Retry Incident',
    });

  beforeEach(() => {
    mockGetIncident().withSuccess(createIncident({state: 'RESOLVED'}));
  });

  it('should display an notification when retrying an incident fails', async () => {
    mockResolveIncident().withServerError(500);
    const {user} = render(<IncidentOperation incidentKey="123" />, {
      wrapper: Wrapper,
    });

    await user.click(retryButton());

    expect(notificationsStore.displayNotification).toHaveBeenCalledTimes(1);
    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'error',
      title: 'Operation could not be created',
      isDismissable: true,
    });
  });

  it('should track successful retry operations', async () => {
    mockResolveIncident().withSuccess(null);
    const spy = vi.spyOn(tracking, 'track').mockReturnValue();
    const {user} = render(<IncidentOperation incidentKey="123" />, {
      wrapper: Wrapper,
    });

    await user.click(retryButton());

    expect(spy).toHaveBeenCalledTimes(1);
    expect(spy).toHaveBeenCalledWith({
      eventName: 'single-operation',
      operationType: 'RESOLVE_INCIDENT',
      source: 'incident-table',
    });
  });

  it('should update job retries before resolving incidents with jobKeys', async () => {
    const updateSpy = vi.fn();
    mockUpdateJob().withSuccess(null, {mockResolverFn: updateSpy});
    const resolveSpy = vi.fn();
    mockResolveIncident().withSuccess(null, {mockResolverFn: resolveSpy});
    const {user} = render(
      <IncidentOperation incidentKey="123" jobKey="456" />,
      {wrapper: Wrapper},
    );

    await user.click(retryButton());

    expect(updateSpy).toHaveBeenCalledTimes(1);
    expect(resolveSpy).toHaveBeenCalledTimes(1);
    expect(updateSpy).toHaveBeenCalledBefore(resolveSpy);
  });

  it('should not resolve incidents when updating the related job fails', async () => {
    mockUpdateJob().withServerError(500);
    const resolveSpy = vi.fn();
    mockResolveIncident().withSuccess(null, {mockResolverFn: resolveSpy});
    const {user} = render(
      <IncidentOperation incidentKey="123" jobKey="456" />,
      {wrapper: Wrapper},
    );

    await user.click(retryButton());

    expect(notificationsStore.displayNotification).toHaveBeenCalledTimes(1);
    expect(resolveSpy).toHaveBeenCalledTimes(0);
  });

  it('should show a spinner and disable the button while the operation is pending', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    mockUpdateJob().withDelay(null);
    mockResolveIncident().withDelay(null);
    const {user} = render(<IncidentOperation incidentKey="123" />, {
      wrapper: Wrapper,
    });

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    await user.click(retryButton());

    expect(retryButton()).toBeDisabled();
    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();

    vi.runOnlyPendingTimers();
    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('operation-spinner'),
    );

    expect(retryButton()).toBeEnabled();
    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();
    vi.useRealTimers();
  });

  it('should poll the incident until its state is RESOLVED', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    const resolveSpy = vi.fn();
    mockResolveIncident().withSuccess(null, {mockResolverFn: resolveSpy});
    const getSpy = vi.fn();
    mockGetIncident().withSuccess(createIncident({state: 'RESOLVED'}), {
      mockResolverFn: getSpy,
    });
    mockGetIncident().withSuccess(createIncident({state: 'ACTIVE'}), {
      mockResolverFn: getSpy,
    });
    const {user} = render(<IncidentOperation incidentKey="123" />, {
      wrapper: Wrapper,
    });

    await user.click(retryButton());
    vi.runOnlyPendingTimers();
    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('operation-spinner'),
    );

    expect(resolveSpy).toHaveBeenCalledTimes(1);
    expect(getSpy).toHaveBeenCalledTimes(2);
    expect(resolveSpy).toHaveBeenCalledBefore(getSpy);
  });
});
