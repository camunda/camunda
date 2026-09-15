/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

function buildInstanceKeyCriterion(includeIds: string[], excludeIds: string[]) {
	if (includeIds.length > 0) {
		return {$in: includeIds};
	}
	if (excludeIds.length > 0) {
		return {$notIn: excludeIds};
	}
	return undefined;
}

export {buildInstanceKeyCriterion};
