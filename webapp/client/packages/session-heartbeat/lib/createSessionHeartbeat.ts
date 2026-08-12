/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const DEFAULT_INTERVAL_MS = 60_000;

const CSRF_TOKEN_HEADER = 'X-CSRF-TOKEN';

const ACTIVITY_EVENTS = ['pointerdown', 'pointermove', 'keydown', 'wheel', 'scroll'] as const;

/*
 * Capture phase so activity is still observed when an application handler calls
 * stopPropagation, and so scroll events — which do not bubble — are seen at all.
 */
const ACTIVITY_LISTENER_OPTIONS: AddEventListenerOptions = {capture: true, passive: true};

type CsrfToken = string | null | undefined;

type SessionHeartbeatFailure =
	| {
			variant: 'network-error';
			response: null;
			networkError: unknown;
	  }
	| {
			variant: 'failed-response';
			response: Response;
			networkError: null;
	  };

type SessionHeartbeatOptions = {
	url: string;
	intervalMs?: number;
	csrfToken?: CsrfToken | (() => CsrfToken);
	onUnauthorized?: () => void;
	onError?: (failure: SessionHeartbeatFailure) => void;
};

function resolveCsrfToken(csrfToken: SessionHeartbeatOptions['csrfToken']): string | null {
	const token = typeof csrfToken === 'function' ? csrfToken() : csrfToken;
	return typeof token === 'string' && token.length > 0 ? token : null;
}

function createSessionHeartbeat({
	url,
	intervalMs = DEFAULT_INTERVAL_MS,
	csrfToken,
	onUnauthorized,
	onError,
}: SessionHeartbeatOptions): () => void {
	/*
	 * Opening the application is itself a user action, so the first tick always sends
	 * one heartbeat — a session that outlives its idle timeout by a single interval is
	 * the price of proving the wiring works from the moment the page loads.
	 */
	let hasActivity = true;
	let pendingRequest: AbortController | null = null;

	function recordActivity() {
		hasActivity = true;
	}

	function handleVisibilityChange() {
		if (document.visibilityState === 'visible') {
			recordActivity();
		}
	}

	async function sendHeartbeat() {
		const request = new AbortController();
		pendingRequest = request;
		const token = resolveCsrfToken(csrfToken);

		try {
			const response = await fetch(url, {
				method: 'POST',
				credentials: 'include',
				headers: token === null ? undefined : {[CSRF_TOKEN_HEADER]: token},
				signal: request.signal,
			});

			if (response.status === 401) {
				onUnauthorized?.();
				return;
			}

			if (!response.ok) {
				onError?.({variant: 'failed-response', response, networkError: null});
			}
		} catch (networkError) {
			if (request.signal.aborted) {
				return;
			}

			onError?.({variant: 'network-error', response: null, networkError});
		} finally {
			if (pendingRequest === request) {
				pendingRequest = null;
			}
		}
	}

	function tick() {
		if (!hasActivity || pendingRequest !== null) {
			return;
		}

		hasActivity = false;
		void sendHeartbeat();
	}

	for (const event of ACTIVITY_EVENTS) {
		document.addEventListener(event, recordActivity, ACTIVITY_LISTENER_OPTIONS);
	}
	document.addEventListener('visibilitychange', handleVisibilityChange);

	const intervalId = window.setInterval(tick, intervalMs);

	return function stop() {
		window.clearInterval(intervalId);

		for (const event of ACTIVITY_EVENTS) {
			document.removeEventListener(event, recordActivity, ACTIVITY_LISTENER_OPTIONS);
		}
		document.removeEventListener('visibilitychange', handleVisibilityChange);

		pendingRequest?.abort();
		pendingRequest = null;
	};
}

export {createSessionHeartbeat, resolveCsrfToken, ACTIVITY_EVENTS, CSRF_TOKEN_HEADER, DEFAULT_INTERVAL_MS};
export type {CsrfToken, SessionHeartbeatFailure, SessionHeartbeatOptions};
