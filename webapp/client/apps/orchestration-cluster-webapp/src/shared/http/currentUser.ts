/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.11';

/**
 * The current user of a caller with a session. The current user query handles an anonymous
 * current user like a 401, so it only ever returns this.
 */
type AuthenticatedCurrentUser = CurrentUser & {anonymous: false; username: string};

/**
 * Tells if a current user response is for a caller without a session. An unprotected API answers
 * such a caller with an anonymous user instead of a 401, because no credentials can authenticate it
 * there.
 */
async function isAnonymousCurrentUser(response: Response): Promise<boolean> {
	try {
		const body: unknown = await response.json();
		return typeof body === 'object' && body !== null && 'anonymous' in body && body.anonymous === true;
	} catch {
		return false;
	}
}

export {isAnonymousCurrentUser, type AuthenticatedCurrentUser};
