/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useEffectEvent, useState} from 'react';

const DEFAULT_FILTER_DEBOUNCE = 500;

function useDebouncedUrlFilter(
	appliedValue: string,
	onCommit: (value: string | undefined) => void,
	delay = DEFAULT_FILTER_DEBOUNCE,
) {
	const [draft, setDraft] = useState(appliedValue);
	const [lastAppliedValue, setLastAppliedValue] = useState(appliedValue);
	const [committedValues, setCommittedValues] = useState<string[]>([]);
	const commit = useEffectEvent(onCommit);

	if (lastAppliedValue !== appliedValue) {
		// Commits land in order, so an acknowledged value retires itself and everything before it.
		const ownIndex = committedValues.indexOf(appliedValue);
		setCommittedValues(ownIndex === -1 ? [] : committedValues.slice(ownIndex + 1));
		// A value we did not commit is navigation: it wins over an edited draft.
		if (draft === lastAppliedValue || ownIndex === -1) {
			setDraft(appliedValue);
		}
		setLastAppliedValue(appliedValue);
	}

	useEffect(() => {
		if (draft === appliedValue) {
			return;
		}

		const timeoutId = setTimeout(() => {
			setCommittedValues((values) => [...values, draft]);
			commit(draft === '' ? undefined : draft);
		}, delay);
		return () => clearTimeout(timeoutId);
	}, [appliedValue, draft, delay]);

	return [draft, setDraft] as const;
}

export {useDebouncedUrlFilter};
