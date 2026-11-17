/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import type {QuerySortOrder} from '@camunda/camunda-api-zod-schemas/8.11';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {getLegacySharedState} from '#/operate/shared/utils/getLegacySharedStateFlag';

const STORAGE_KEY = 'operate.instanceHistorySortOrder';
const preferenceSchema = z.enum(['asc', 'desc']);

function getHistorySortOrder(): QuerySortOrder {
	const stored = getStateLocally(STORAGE_KEY) ?? getLegacySharedState('instanceHistorySortOrder', preferenceSchema);
	return stored === 'asc' ? 'ASC' : 'DESC';
}

function setHistorySortOrder(order: QuerySortOrder) {
	storeStateLocally(STORAGE_KEY, order === 'ASC' ? 'asc' : 'desc');
}

export {getHistorySortOrder, setHistorySortOrder};
