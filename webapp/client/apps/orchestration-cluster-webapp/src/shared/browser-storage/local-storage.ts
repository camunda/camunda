/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {createBrowserStorage} from './createBrowserStorage';
import {namedCustomFiltersSchema} from '#/tasklist/modules/available-tasks/customFiltersSchema';

const {
	store: storeStateLocally,
	get: getStateLocally,
	clear: clearStateLocally,
} = createBrowserStorage(localStorage, {
	theme: z.enum(['light', 'dark', 'system']),
	wasReloaded: z.boolean(),
	'tasklist.hasCompletedTask': z.boolean(),
	'tasklist.customFilters': z.record(z.string(), namedCustomFiltersSchema),
	'operate.panelStates': z.record(z.string(), z.union([z.boolean(), z.array(z.number())])),
});

export {storeStateLocally, getStateLocally, clearStateLocally};
