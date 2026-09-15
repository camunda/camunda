/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo, useSyncExternalStore} from 'react';
import {breakpoints} from '@carbon/layout';

function useMatchMedia(mediaQuery: string): boolean {
	const query = useMemo(() => window.matchMedia(mediaQuery), [mediaQuery]);
	const subscribe = useCallback(
		(listener: () => void) => {
			query.addEventListener('change', listener);
			return () => query.removeEventListener('change', listener);
		},
		[query],
	);

	return useSyncExternalStore(subscribe, () => query.matches);
}

function isWidthBelowBreakpoint(name: keyof typeof breakpoints): string {
	return `(max-width: ${breakpoints[name].width})`;
}

export {useMatchMedia, isWidthBelowBreakpoint};
