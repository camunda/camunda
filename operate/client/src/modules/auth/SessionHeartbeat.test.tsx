/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {act} from 'react';
import {render, waitFor} from 'modules/testing-library';
import {authenticationStore} from 'modules/stores/authentication';
import {mockPostRequest} from 'modules/mocks/api/mockRequest';
import {SessionHeartbeat} from './SessionHeartbeat';

const HEARTBEAT_INTERVAL = 60_000;

const mockHeartbeat = () => mockPostRequest<null>('/session/heartbeat');

describe('SessionHeartbeat', () => {
  afterEach(() => {
    act(() => authenticationStore.reset());
    vi.useRealTimers();
  });

  it('should send a heartbeat while logged in', async () => {
    const heartbeat = vi.fn();
    mockHeartbeat().withSuccess(null, {mockResolverFn: heartbeat});
    mockHeartbeat().withSuccess(null, {mockResolverFn: heartbeat});
    vi.useFakeTimers({shouldAdvanceTime: true});
    act(() => authenticationStore.activateSession());

    render(<SessionHeartbeat />);
    await vi.advanceTimersByTimeAsync(HEARTBEAT_INTERVAL);

    await waitFor(() => expect(heartbeat).toHaveBeenCalledTimes(1));

    document.dispatchEvent(new KeyboardEvent('keydown'));
    await vi.advanceTimersByTimeAsync(HEARTBEAT_INTERVAL);

    await waitFor(() => expect(heartbeat).toHaveBeenCalledTimes(2));
  });

  it('should not send a heartbeat before the session is established', async () => {
    const heartbeat = vi.fn();
    mockHeartbeat().withSuccess(null, {mockResolverFn: heartbeat});
    vi.useFakeTimers({shouldAdvanceTime: true});

    render(<SessionHeartbeat />);
    await vi.advanceTimersByTimeAsync(HEARTBEAT_INTERVAL * 3);

    expect(heartbeat).not.toHaveBeenCalled();
  });

  it('should stop sending heartbeats once the session ends', async () => {
    const heartbeat = vi.fn();
    mockHeartbeat().withSuccess(null, {mockResolverFn: heartbeat});
    mockHeartbeat().withSuccess(null, {mockResolverFn: heartbeat});
    vi.useFakeTimers({shouldAdvanceTime: true});
    act(() => authenticationStore.activateSession());

    render(<SessionHeartbeat />);
    await vi.advanceTimersByTimeAsync(HEARTBEAT_INTERVAL);
    await waitFor(() => expect(heartbeat).toHaveBeenCalledTimes(1));

    act(() => authenticationStore.disableSession());
    document.dispatchEvent(new KeyboardEvent('keydown'));
    await vi.advanceTimersByTimeAsync(HEARTBEAT_INTERVAL * 3);

    expect(heartbeat).toHaveBeenCalledTimes(1);
  });
});
