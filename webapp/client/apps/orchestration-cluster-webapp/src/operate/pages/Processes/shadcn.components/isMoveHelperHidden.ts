/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getStateLocally} from '#/shared/browser-storage/local-storage';
import {getLegacySharedStateFlag} from '#/operate/shared/utils/getLegacySharedStateFlag';

const HELPER_STORAGE_KEY = 'operate.hideMoveModificationHelperModal';

function isMoveHelperHidden() {
	return getStateLocally(HELPER_STORAGE_KEY) ?? getLegacySharedStateFlag('hideMoveModificationHelperModal');
}

export {HELPER_STORAGE_KEY, isMoveHelperHidden};
