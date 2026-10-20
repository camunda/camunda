/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {getLegacySharedStateFlag} from '#/operate/shared/utils/getLegacySharedStateFlag';
import type {MappingType} from './getMappings';

const STORAGE_KEYS = {
	Input: 'operate.hideInputMappingsHelperBanner',
	Output: 'operate.hideOutputMappingsHelperBanner',
} as const;

const LEGACY_FLAGS = {
	Input: 'hideInputMappingsHelperBanner',
	Output: 'hideOutputMappingsHelperBanner',
} as const;

function isMappingsHelperBannerHidden(type: MappingType) {
	return getStateLocally(STORAGE_KEYS[type]) ?? getLegacySharedStateFlag(LEGACY_FLAGS[type]);
}

function setMappingsHelperBannerHidden(type: MappingType) {
	storeStateLocally(STORAGE_KEYS[type], true);
}

export {isMappingsHelperBannerHidden, setMappingsHelperBannerHidden};
