/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {createSessionHeartbeat, resolveCsrfToken, type SessionHeartbeatOptions} from './createSessionHeartbeat';

type UseSessionHeartbeatOptions = SessionHeartbeatOptions & {
	enabled?: boolean;
};

function useSessionHeartbeat({enabled = true, url, intervalMs, ...perRequestOptions}: UseSessionHeartbeatOptions) {
	/*
	 * The CSRF token and the callbacks are read at request time from this ref, so a
	 * consumer passing inline functions does not tear down and restart the heartbeat
	 * on every render.
	 */
	const perRequestOptionsRef = useRef(perRequestOptions);

	/*
	 * Deliberately no deps array: this must run after every render to keep the ref
	 * current. Passing [perRequestOptions] would behave identically -- rest
	 * destructuring builds a new object each render -- while implying a
	 * memoization that does not exist.
	 */
	useEffect(() => {
		perRequestOptionsRef.current = perRequestOptions;
	});

	useEffect(() => {
		if (!enabled) {
			return;
		}

		return createSessionHeartbeat({
			url,
			intervalMs,
			csrfToken: () => resolveCsrfToken(perRequestOptionsRef.current.csrfToken),
			onUnauthorized: () => perRequestOptionsRef.current.onUnauthorized?.(),
			onError: (failure) => perRequestOptionsRef.current.onError?.(failure),
		});
	}, [enabled, url, intervalMs]);
}

export {useSessionHeartbeat};
export type {UseSessionHeartbeatOptions};
