/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {http, HttpResponse} from 'msw';
import {act, render, waitFor} from 'modules/testing/testing-library';
import {nodeMockServer} from 'modules/testing/nodeMockServer';
import {authenticationStore} from 'modules/auth/authentication';
import {SessionHeartbeat} from './SessionHeartbeat';

const HEARTBEAT_INTERVAL = 60_000;

function mockHeartbeat() {
  const heartbeat = vi.fn();

  nodeMockServer.use(
    http.post('/session/heartbeat', () => {
      heartbeat();
      return new HttpResponse(null, {status: 204});
    }),
  );

  return heartbeat;
}

describe('SessionHeartbeat', () => {
  afterEach(() => {
    act(() => authenticationStore.reset());
    vi.useRealTimers();
  });

  it('should send a heartbeat while logged in', async () => {
    const heartbeat = mockHeartbeat();
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
    const heartbeat = mockHeartbeat();
    vi.useFakeTimers({shouldAdvanceTime: true});

    render(<SessionHeartbeat />);
    await vi.advanceTimersByTimeAsync(HEARTBEAT_INTERVAL * 3);

    expect(heartbeat).not.toHaveBeenCalled();
  });

  it('should stop sending heartbeats once the session ends', async () => {
    const heartbeat = mockHeartbeat();
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
