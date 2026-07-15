/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {parse, isValid} from 'date-fns';

function parseFilterTime(value: string) {
	const HOUR_MINUTES_PATTERN = /^[0-9]{2}:[0-9]{2}$/;
	const HOUR_MINUTES_SECONDS_PATTERN = /^[0-9]{2}:[0-9]{2}:[0-9]{2}$/;

	if (HOUR_MINUTES_PATTERN.test(value)) {
		const parsedDate = parse(value, 'HH:mm', new Date());
		return isValid(parsedDate) ? parsedDate : undefined;
	}

	if (HOUR_MINUTES_SECONDS_PATTERN.test(value)) {
		const parsedDate = parse(value, 'HH:mm:ss', new Date());
		return isValid(parsedDate) ? parsedDate : undefined;
	}
	return undefined;
}

export {parseFilterTime};
