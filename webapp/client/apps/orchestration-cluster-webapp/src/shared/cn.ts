/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type ClassValue = string | undefined | null | false | Record<string, unknown>;

function cn(...args: ClassValue[]): string {
	const classes: string[] = [];

	for (const arg of args) {
		if (typeof arg === 'string') {
			classes.push(arg);
		} else if (typeof arg === 'object' && arg !== null) {
			for (const key of Object.keys(arg)) {
				if (arg[key]) {
					classes.push(key);
				}
			}
		}
	}

	return classes.join(' ');
}

export {cn};
