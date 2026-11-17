/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestErrorSchema} from '#/shared/http/request';

// A group's ID is the only server-side uniqueness constraint, so any 409 on create is a duplicate ID.
function isDuplicateGroupIdError(error: unknown): boolean {
	const requestError = requestErrorSchema.safeParse(error);

	return (
		requestError.success && requestError.data.variant === 'failed-response' && requestError.data.response.status === 409
	);
}

export {isDuplicateGroupIdError};
