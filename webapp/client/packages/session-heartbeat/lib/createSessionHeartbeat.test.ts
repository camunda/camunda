/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {createSessionHeartbeat} from './createSessionHeartbeat';

const HEARTBEAT_URL = '/session/heartbeat';
const INTERVAL_MS = 1000;

let stopHeartbeat: (() => void) | null = null;

/*
 * The heartbeat has no request layer to mock with MSW — the assertions are about when
 * a fetch happens and with which headers, so the global fetch is stubbed directly.
 */
function stubFetch(respond: typeof fetch = async () => new Response(null, {status: 204})) {
	const fetchSpy = vi.fn<typeof fetch>(respond);
	vi.stubGlobal('fetch', fetchSpy);
	return fetchSpy;
}

function stubVisibilityState(visibilityState: DocumentVisibilityState) {
	Object.defineProperty(document, 'visibilityState', {
		configurable: true,
		get: () => visibilityState,
	});
}

function start(options: Partial<Parameters<typeof createSessionHeartbeat>[0]> = {}) {
	stopHeartbeat = createSessionHeartbeat({url: HEARTBEAT_URL, intervalMs: INTERVAL_MS, ...options});
}

async function elapse(intervals = 1) {
	await vi.advanceTimersByTimeAsync(INTERVAL_MS * intervals);
}

describe('createSessionHeartbeat', () => {
	beforeEach(() => {
		vi.useFakeTimers({toFake: ['setInterval', 'clearInterval']});
	});

	afterEach(() => {
		stopHeartbeat?.();
		stopHeartbeat = null;
		Reflect.deleteProperty(document, 'visibilityState');
		vi.unstubAllGlobals();
		vi.useRealTimers();
	});

	it('should send a heartbeat on the first interval after start', async () => {
		const fetchSpy = stubFetch();

		start();
		await elapse();

		expect(fetchSpy).toHaveBeenCalledTimes(1);
		expect(fetchSpy).toHaveBeenCalledWith(
			HEARTBEAT_URL,
			expect.objectContaining({method: 'POST', credentials: 'include'}),
		);
	});

	it('should not send a heartbeat before the first interval elapses', async () => {
		const fetchSpy = stubFetch();

		start();
		await vi.advanceTimersByTimeAsync(INTERVAL_MS - 1);

		expect(fetchSpy).not.toHaveBeenCalled();
	});

	it('should not send a heartbeat for an interval without user activity', async () => {
		const fetchSpy = stubFetch();

		start();
		await elapse(5);

		expect(fetchSpy).toHaveBeenCalledTimes(1);
	});

	it('should send a heartbeat again once user activity resumes', async () => {
		const fetchSpy = stubFetch();

		start();
		await elapse(3);
		document.dispatchEvent(new KeyboardEvent('keydown'));
		await elapse();

		expect(fetchSpy).toHaveBeenCalledTimes(2);
	});

	it.for(['pointerdown', 'pointermove', 'keydown', 'wheel', 'scroll'])(
		'should count a %s event as user activity',
		async (eventType, {expect}) => {
			const fetchSpy = stubFetch();

			start();
			await elapse();
			document.dispatchEvent(new Event(eventType));
			await elapse();

			expect(fetchSpy).toHaveBeenCalledTimes(2);
		},
	);

	it('should send at most one heartbeat per interval no matter how much activity happens', async () => {
		const fetchSpy = stubFetch();

		start();
		await elapse();
		for (let index = 0; index < 50; index++) {
			document.dispatchEvent(new PointerEvent('pointermove'));
		}
		await elapse();

		expect(fetchSpy).toHaveBeenCalledTimes(2);
	});

	it('should count the tab becoming visible as user activity', async () => {
		const fetchSpy = stubFetch();
		stubVisibilityState('visible');

		start();
		await elapse();
		document.dispatchEvent(new Event('visibilitychange'));
		await elapse();

		expect(fetchSpy).toHaveBeenCalledTimes(2);
	});

	it('should not count the tab becoming hidden as user activity', async () => {
		const fetchSpy = stubFetch();
		stubVisibilityState('hidden');

		start();
		await elapse();
		document.dispatchEvent(new Event('visibilitychange'));
		await elapse();

		expect(fetchSpy).toHaveBeenCalledTimes(1);
	});

	it('should not send a heartbeat while a previous one is still in flight', async () => {
		const fetchSpy = stubFetch(() => new Promise<Response>(() => {}));

		start();
		await elapse();
		document.dispatchEvent(new KeyboardEvent('keydown'));
		await elapse();

		expect(fetchSpy).toHaveBeenCalledTimes(1);
	});

	it('should stop sending heartbeats after being stopped', async () => {
		const fetchSpy = stubFetch();

		start();
		await elapse();
		stopHeartbeat?.();
		document.dispatchEvent(new KeyboardEvent('keydown'));
		await elapse(3);

		expect(fetchSpy).toHaveBeenCalledTimes(1);
	});

	describe('CSRF token', () => {
		it('should send a token provided as a value', async () => {
			const fetchSpy = stubFetch();

			start({csrfToken: 'token-from-a-value'});
			await elapse();

			expect(fetchSpy).toHaveBeenCalledWith(
				HEARTBEAT_URL,
				expect.objectContaining({headers: {'X-CSRF-TOKEN': 'token-from-a-value'}}),
			);
		});

		it('should read a token provided as a getter on every heartbeat', async () => {
			const fetchSpy = stubFetch();
			const tokens = ['first-token', 'second-token'];

			start({csrfToken: () => tokens.shift() ?? null});
			await elapse();
			document.dispatchEvent(new KeyboardEvent('keydown'));
			await elapse();

			expect(fetchSpy.mock.calls.map(([, init]) => init?.headers)).toEqual([
				{'X-CSRF-TOKEN': 'first-token'},
				{'X-CSRF-TOKEN': 'second-token'},
			]);
		});

		it('should omit the header when no token is available', async () => {
			const fetchSpy = stubFetch();

			start({csrfToken: () => null});
			await elapse();

			expect(fetchSpy).toHaveBeenCalledWith(HEARTBEAT_URL, expect.objectContaining({headers: undefined}));
		});
	});

	describe('failures', () => {
		it('should report an expired session through onUnauthorized', async () => {
			stubFetch(async () => new Response(null, {status: 401}));
			const onUnauthorized = vi.fn();
			const onError = vi.fn();

			start({onUnauthorized, onError});
			await elapse();

			expect(onUnauthorized).toHaveBeenCalledTimes(1);
			expect(onError).not.toHaveBeenCalled();
		});

		it('should keep sending heartbeats after an expired session so a recovered session is kept alive', async () => {
			const fetchSpy = stubFetch(async () => new Response(null, {status: 401}));

			start();
			await elapse();
			document.dispatchEvent(new KeyboardEvent('keydown'));
			await elapse();

			expect(fetchSpy).toHaveBeenCalledTimes(2);
		});

		it('should report a failed response through onError', async () => {
			stubFetch(async () => new Response(null, {status: 500}));
			const onError = vi.fn();

			start({onError});
			await elapse();

			expect(onError).toHaveBeenCalledWith({
				variant: 'failed-response',
				response: expect.objectContaining({status: 500}),
				networkError: null,
			});
		});

		it('should report a network error through onError', async () => {
			const networkError = new Error('offline');
			stubFetch(() => Promise.reject(networkError));
			const onError = vi.fn();

			start({onError});
			await elapse();

			expect(onError).toHaveBeenCalledWith({
				variant: 'network-error',
				response: null,
				networkError,
			});
		});

		it('should not report an error when a heartbeat is aborted by stopping', async () => {
			stubFetch(
				(_url, init) =>
					new Promise<Response>((_, reject) => {
						init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
					}),
			);
			const onError = vi.fn();

			start({onError});
			await elapse();
			stopHeartbeat?.();
			await elapse();

			expect(onError).not.toHaveBeenCalled();
		});
	});
});
