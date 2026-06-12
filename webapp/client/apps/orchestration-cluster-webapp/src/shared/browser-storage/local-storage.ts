/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {createBrowserStorage} from './createBrowserStorage';

const {
	store: storeStateLocally,
	get: getStateLocally,
	clear: clearStateLocally,
} = createBrowserStorage(localStorage, {
	theme: z.enum(['light', 'dark', 'system']),
	wasReloaded: z.boolean(),
	hasCompletedTask: z.boolean(),
});

export {storeStateLocally, getStateLocally, clearStateLocally};
