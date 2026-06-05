/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {GetSystemConfigurationResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {getSessionState} from '#/shared/browser-storage/session-storage';

function getClientConfig(): GetSystemConfigurationResponseBody {
	const config = getSessionState('clientConfig');
	if (config === null) {
		throw new Error('Client config not initialized. This can only be called after authentication.');
	}
	return config;
}

export {getClientConfig};
