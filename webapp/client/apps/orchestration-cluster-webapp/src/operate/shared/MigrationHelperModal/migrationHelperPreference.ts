/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {getLegacySharedStateFlag} from '#/operate/shared/utils/getLegacySharedStateFlag';

const STORAGE_KEY = 'operate.hideMigrationHelperModal';

function isMigrationHelperHidden() {
	return getStateLocally(STORAGE_KEY) ?? getLegacySharedStateFlag('hideMigrationHelperModal');
}

function setMigrationHelperHidden(isHidden: boolean) {
	storeStateLocally(STORAGE_KEY, isHidden);
}

export {isMigrationHelperHidden, setMigrationHelperHidden};
