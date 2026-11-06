/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const DOCS_BASE = 'https://docs.camunda.io/docs/next';

const GUIDE_URLS = {
	users: `${DOCS_BASE}/components/admin/user/`,
	groups: `${DOCS_BASE}/components/admin/group/`,
	roles: `${DOCS_BASE}/components/admin/role/`,
	tenants: `${DOCS_BASE}/components/admin/tenant/`,
	mappingRules: `${DOCS_BASE}/components/admin/mapping-rules/`,
	clients: `${DOCS_BASE}/components/admin/client/`,
	authorizations: `${DOCS_BASE}/components/admin/authorization/`,
	clusterVariables: `${DOCS_BASE}/components/modeler/feel/cluster-variable/cluster-variable-overview/`,
	globalTaskListeners: `${DOCS_BASE}/components/concepts/global-user-task-listeners/`,
	mcpProcesses: `${DOCS_BASE}/apis-tools/processes-mcp/processes-mcp-overview/`,
} as const;

export {GUIDE_URLS};
