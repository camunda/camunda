/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ForbiddenError} from '#/shared/errors';
import type {RequestError} from './request';

function mapQueryError(error: RequestError): RequestError | ForbiddenError {
	if (error.response?.status === 403) {
		return new ForbiddenError();
	}

	return error;
}

export {mapQueryError};
