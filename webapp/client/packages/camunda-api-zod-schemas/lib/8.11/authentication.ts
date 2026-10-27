/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {camundaUserResultSchema} from './gen/zod/camundaUserResultSchema';
import type {CamundaUserResult} from './gen/types/CamundaUserResult';

const currentUserSchema = camundaUserResultSchema;
type CurrentUser = CamundaUserResult;

/**
 * Tells if a current user is the anonymous caller of an unprotected API, which answers a caller
 * without a session this way instead of with a 401. A client handles it like that 401.
 */
function isAnonymousCurrentUser(currentUser: unknown): boolean {
	return (
		typeof currentUser === 'object' &&
		currentUser !== null &&
		'anonymous' in currentUser &&
		currentUser.anonymous === true
	);
}

const getCurrentUser = {
	method: 'GET',
	getUrl: () => `/${API_VERSION}/authentication/me` as const,
} as const satisfies Endpoint;

export {currentUserSchema, getCurrentUser, isAnonymousCurrentUser};

export type {CurrentUser};
