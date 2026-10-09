/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useEffectEvent, useState} from 'react';

const DEFAULT_FILTER_DEBOUNCE = 500;

/**
 * Keeps a text input responsive while committing its value to the URL only once typing stops.
 *
 * Back/forward navigation changes `appliedValue` without touching the draft, which would
 * otherwise leave the input showing a value the table is no longer filtered by. The draft is
 * only resynced when it still matches what was last applied, so a slow round-trip for an
 * earlier commit can't clobber input typed in the meantime.
 *
 * An empty draft is committed as `undefined` so the filter is dropped from the URL.
 */
function useDebouncedUrlFilter(
	appliedValue: string,
	onCommit: (value: string | undefined) => void,
	delay = DEFAULT_FILTER_DEBOUNCE,
) {
	const [draft, setDraft] = useState(appliedValue);
	const [lastAppliedValue, setLastAppliedValue] = useState(appliedValue);
	const commit = useEffectEvent(onCommit);

	if (lastAppliedValue !== appliedValue) {
		if (draft === lastAppliedValue) {
			setDraft(appliedValue);
		}
		setLastAppliedValue(appliedValue);
	}

	useEffect(() => {
		if (draft === appliedValue) {
			return;
		}

		const timeoutId = setTimeout(() => commit(draft === '' ? undefined : draft), delay);
		return () => clearTimeout(timeoutId);
	}, [appliedValue, draft, delay]);

	return [draft, setDraft] as const;
}

export {useDebouncedUrlFilter};
