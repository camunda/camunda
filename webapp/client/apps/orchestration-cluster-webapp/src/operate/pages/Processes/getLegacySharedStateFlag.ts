/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';

type LegacySharedStateFlag = 'hideMoveModificationHelperModal';

function getLegacySharedStateFlag(key: LegacySharedStateFlag) {
	try {
		const result = z.object({[key]: z.boolean()}).safeParse(JSON.parse(localStorage.getItem('sharedState') ?? '{}'));
		return result.success && result.data[key] === true;
	} catch {
		return false;
	}
}

export {getLegacySharedStateFlag};
