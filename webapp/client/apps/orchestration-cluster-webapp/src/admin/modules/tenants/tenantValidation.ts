/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Mirrors the tenant ID constraint of the create-tenant API: up to 31 word characters, `.` or `-`.
const TENANT_ID_PATTERN = /^[\w.-]{1,31}$/;
const MAX_DESCRIPTION_LENGTH = 255;

function isValidTenantId(value: string): boolean {
	return TENANT_ID_PATTERN.test(value);
}

export {MAX_DESCRIPTION_LENGTH, isValidTenantId};
