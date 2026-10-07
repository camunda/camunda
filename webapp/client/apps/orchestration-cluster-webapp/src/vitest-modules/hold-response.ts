/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {onTestFinished} from 'vitest';

/**
 * Holds a mocked response until `release` is called, so a test can assert a pending state first.
 * Released automatically when the test finishes, so a failed test never leaves a handler waiting.
 */
function holdResponse() {
	let release!: () => void;
	const held = new Promise<void>((resolve) => {
		release = resolve;
	});
	onTestFinished(release);
	return {held, release};
}

export {holdResponse};
