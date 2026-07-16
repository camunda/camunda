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
function parseSearchValueSafe(raw: string) {
	if (/^-?\d+$/.test(raw)) {
		const num = Number(raw);
		if (!Number.isSafeInteger(num) || String(num) !== raw) {
			return raw;
		}
	}
	try {
		return JSON.parse(raw);
	} catch {
		return raw;
	}
}

export {parseSearchValueSafe};
