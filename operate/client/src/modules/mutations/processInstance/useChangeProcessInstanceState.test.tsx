/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockSuspendProcessInstance} from 'modules/mocks/api/v2/processInstances/suspendProcessInstance';
import {mockResumeProcessInstance} from 'modules/mocks/api/v2/processInstances/resumeProcessInstance';
import {createProcessInstance} from 'modules/testUtils';
import {queryKeys} from 'modules/queries/queryKeys';
import {useSuspendProcessInstance} from './useSuspendProcessInstance';
import {useResumeProcessInstance} from './useResumeProcessInstance';
import type {StateChangeError} from './useChangeProcessInstanceState';

const processInstanceKey = '2251799813685294';

type TestComponentProps = {
  action: 'suspend' | 'resume';
  onSuccess?: () => void;
  onError?: (error: StateChangeError) => void;
};

const TestComponent: React.FC<TestComponentProps> = ({
  action,
  onSuccess,
  onError,
}) => {
  const suspend = useSuspendProcessInstance(processInstanceKey, {
    onSuccess,
    onError,
  });
  const resume = useResumeProcessInstance(processInstanceKey, {
    onSuccess,
    onError,
  });
  const {mutate, status} = action === 'suspend' ? suspend : resume;

  return (
    <button onClick={() => mutate()} disabled={status === 'pending'}>
      {status}
    </button>
  );
};

const renderTestComponent = (props: TestComponentProps) => {
  const queryClient = getMockQueryClient();
  const {user} = render(
    <QueryClientProvider client={queryClient}>
      <TestComponent {...props} />
    </QueryClientProvider>,
  );
  return {queryClient, user};
};

describe('useChangeProcessInstanceState', () => {
  it('should reflect the SUSPENDED state once the suspend mutation completes', async () => {
    const onSuccess = vi.fn();
    mockSuspendProcessInstance().withSuccess(null);
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({processInstanceKey, state: 'SUSPENDED'}),
    );

    const {user, queryClient} = renderTestComponent({
      action: 'suspend',
      onSuccess,
    });
    await user.click(screen.getByRole('button'));

    await waitFor(() => {
      expect(screen.getByRole('button')).toHaveTextContent('success');
    });

    expect(onSuccess).toHaveBeenCalledTimes(1);
    expect(
      queryClient.getQueryData(
        queryKeys.processInstance.get(processInstanceKey),
      ),
    ).toEqual(expect.objectContaining({state: 'SUSPENDED'}));
  });

  it('should reflect the ACTIVE state once the resume mutation completes', async () => {
    mockResumeProcessInstance().withSuccess(null);
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({processInstanceKey, state: 'ACTIVE'}),
    );

    const {user, queryClient} = renderTestComponent({action: 'resume'});
    await user.click(screen.getByRole('button'));

    await waitFor(() => {
      expect(screen.getByRole('button')).toHaveTextContent('success');
    });

    expect(
      queryClient.getQueryData(
        queryKeys.processInstance.get(processInstanceKey),
      ),
    ).toEqual(expect.objectContaining({state: 'ACTIVE'}));
  });

  it('should treat any non-SUSPENDED state as resumed, including a terminal state', async () => {
    mockResumeProcessInstance().withSuccess(null);
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({processInstanceKey, state: 'COMPLETED'}),
    );

    const {user, queryClient} = renderTestComponent({action: 'resume'});
    await user.click(screen.getByRole('button'));

    await waitFor(() => {
      expect(screen.getByRole('button')).toHaveTextContent('success');
    });

    expect(
      queryClient.getQueryData(
        queryKeys.processInstance.get(processInstanceKey),
      ),
    ).toEqual(expect.objectContaining({state: 'COMPLETED'}));
  });

  it('should fail without polling the process instance when the suspend request itself fails', async () => {
    const onError = vi.fn();
    let verificationRequested = false;
    mockSuspendProcessInstance().withServerError(500);
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({processInstanceKey, state: 'SUSPENDED'}),
      {
        mockResolverFn: vi.fn(() => {
          verificationRequested = true;
        }),
      },
    );

    const {user} = renderTestComponent({action: 'suspend', onError});
    await user.click(screen.getByRole('button'));

    await waitFor(() => {
      expect(screen.getByRole('button')).toHaveTextContent('error');
    });

    expect(onError).toHaveBeenCalledTimes(1);
    expect(verificationRequested).toBe(false);
  });

  it('should keep retrying verification until the process instance reaches the expected state', async () => {
    mockSuspendProcessInstance().withSuccess(null);
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({processInstanceKey, state: 'ACTIVE'}),
    );
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({processInstanceKey, state: 'SUSPENDED'}),
    );

    const {user, queryClient} = renderTestComponent({action: 'suspend'});
    await user.click(screen.getByRole('button'));

    await waitFor(
      () => {
        expect(screen.getByRole('button')).toHaveTextContent('success');
      },
      {timeout: 5000},
    );

    expect(
      queryClient.getQueryData(
        queryKeys.processInstance.get(processInstanceKey),
      ),
    ).toEqual(expect.objectContaining({state: 'SUSPENDED'}));
  });

  it('should invalidate the process instances list cache on success', async () => {
    mockSuspendProcessInstance().withSuccess(null);
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({processInstanceKey, state: 'SUSPENDED'}),
    );

    const {user, queryClient} = renderTestComponent({action: 'suspend'});
    const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries');

    await user.click(screen.getByRole('button'));

    await waitFor(() => {
      expect(screen.getByRole('button')).toHaveTextContent('success');
    });

    expect(invalidateQueriesSpy).toHaveBeenCalledWith(
      expect.objectContaining({queryKey: queryKeys.processInstances.base()}),
    );
  });
});
