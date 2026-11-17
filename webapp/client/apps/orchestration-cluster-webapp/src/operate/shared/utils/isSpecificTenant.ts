/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/** The `TenantField` uses the literal `'all'` as its "All tenants" item id (see legacy parity). */
function isSpecificTenant(tenantId: string | undefined): tenantId is string {
	return tenantId !== undefined && tenantId !== 'all';
}

export {isSpecificTenant};
