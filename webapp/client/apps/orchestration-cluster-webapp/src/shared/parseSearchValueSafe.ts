/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Zeebe keys are 64-bit and can exceed Number.MAX_SAFE_INTEGER. TanStack Router's
// default search-param parser (JSON.parse) would silently round a bare integer
// value past that range before any route's own validateSearch runs, corrupting
// keys in copy-pasted or hand-built URLs. Keep integer-looking values that
// JSON.parse can't round-trip exactly as strings instead of parsing them.
//
// This must behave like a JSON.parse drop-in otherwise, including throwing on
// non-JSON input (e.g. a plain string like "custom") — parseSearchWith and
// stringifySearchWith each wrap calls to this in their own try/catch and rely
// on that throw to decide a value needs no further parsing/quoting. Swallowing
// it here would make stringifySearchWith treat every string as "parseable"
// and wrap it in quotes, corrupting every plain string search param.
function parseSearchValueSafe(raw: string) {
	if (/^-?\d+$/.test(raw)) {
		const num = Number(raw);
		if (!Number.isSafeInteger(num) || (raw !== '-0' && String(num) !== raw)) {
			return raw;
		}
	}
	return JSON.parse(raw);
}

export {parseSearchValueSafe};
