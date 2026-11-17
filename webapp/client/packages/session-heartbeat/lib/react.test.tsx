/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {render} from 'vitest-browser-react';
import type {FC} from 'react';
import {useSessionHeartbeat, type UseSessionHeartbeatOptions} from './react';

const HEARTBEAT_URL = '/session/heartbeat';
const INTERVAL_MS = 1000;

function stubFetch(status = 204) {
	const fetchSpy = vi.fn<typeof fetch>(async () => new Response(null, {status}));
	vi.stubGlobal('fetch', fetchSpy);
	return fetchSpy;
}

const Heartbeat: FC<Omit<UseSessionHeartbeatOptions, 'url' | 'intervalMs'>> = (options) => {
	useSessionHeartbeat({url: HEARTBEAT_URL, intervalMs: INTERVAL_MS, ...options});
	return null;
};

async function elapse(intervals = 1) {
	await vi.advanceTimersByTimeAsync(INTERVAL_MS * intervals);
}

describe('useSessionHeartbeat', () => {
	beforeEach(() => {
		vi.useFakeTimers({toFake: ['setInterval', 'clearInterval']});
	});

	afterEach(() => {
		vi.unstubAllGlobals();
		vi.useRealTimers();
	});

	it('should send heartbeats while mounted', async () => {
		const fetchSpy = stubFetch();

		await render(<Heartbeat />);
		await elapse();

		expect(fetchSpy).toHaveBeenCalledTimes(1);
	});

	it('should stop sending heartbeats once unmounted', async () => {
		const fetchSpy = stubFetch();

		const screen = await render(<Heartbeat />);
		await elapse();
		await screen.unmount();
		document.dispatchEvent(new KeyboardEvent('keydown'));
		await elapse(3);

		expect(fetchSpy).toHaveBeenCalledTimes(1);
	});

	it('should not send heartbeats while disabled', async () => {
		const fetchSpy = stubFetch();

		await render(<Heartbeat enabled={false} />);
		await elapse(3);

		expect(fetchSpy).not.toHaveBeenCalled();
	});

	it('should start sending heartbeats when it becomes enabled', async () => {
		const fetchSpy = stubFetch();

		const screen = await render(<Heartbeat enabled={false} />);
		await elapse();
		await screen.rerender(<Heartbeat enabled />);
		await elapse();

		expect(fetchSpy).toHaveBeenCalledTimes(1);
	});

	it('should call the callbacks from the latest render without restarting the heartbeat', async () => {
		const fetchSpy = stubFetch(401);
		const staleOnUnauthorized = vi.fn();
		const latestOnUnauthorized = vi.fn();

		const screen = await render(<Heartbeat onUnauthorized={staleOnUnauthorized} />);
		await screen.rerender(<Heartbeat onUnauthorized={latestOnUnauthorized} />);
		await elapse();

		expect(fetchSpy).toHaveBeenCalledTimes(1);
		expect(staleOnUnauthorized).not.toHaveBeenCalled();
		expect(latestOnUnauthorized).toHaveBeenCalledTimes(1);
	});
});
