/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';

function useDebouncedValue<T>(value: T, delay: number): T {
	const [debounced, setDebounced] = useState(value);

	useEffect(() => {
		const timeoutId = setTimeout(() => setDebounced(value), delay);
		return () => clearTimeout(timeoutId);
	}, [value, delay]);

	return debounced;
}

export {useDebouncedValue};
