/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {TFunction} from 'i18next';
import type {ResourceType} from '@camunda/camunda-api-zod-schemas/8.11';

function getResourceTypeLabel(resourceType: ResourceType, t: TFunction): string {
	const labels: Record<ResourceType, string> = {
		AUDIT_LOG: t('admin.authorizations.resourceTypes.AUDIT_LOG'),
		AUTHORIZATION: t('admin.authorizations.resourceTypes.AUTHORIZATION'),
		BACKUP: t('admin.authorizations.resourceTypes.BACKUP'),
		BATCH: t('admin.authorizations.resourceTypes.BATCH'),
		CLUSTER_VARIABLE: t('admin.authorizations.resourceTypes.CLUSTER_VARIABLE'),
		COMPONENT: t('admin.authorizations.resourceTypes.COMPONENT'),
		DECISION_DEFINITION: t('admin.authorizations.resourceTypes.DECISION_DEFINITION'),
		DECISION_REQUIREMENTS_DEFINITION: t('admin.authorizations.resourceTypes.DECISION_REQUIREMENTS_DEFINITION'),
		DOCUMENT: t('admin.authorizations.resourceTypes.DOCUMENT'),
		EXPORTER: t('admin.authorizations.resourceTypes.EXPORTER'),
		EXPRESSION: t('admin.authorizations.resourceTypes.EXPRESSION'),
		GLOBAL_LISTENER: t('admin.authorizations.resourceTypes.GLOBAL_LISTENER'),
		GROUP: t('admin.authorizations.resourceTypes.GROUP'),
		MAPPING_RULE: t('admin.authorizations.resourceTypes.MAPPING_RULE'),
		MESSAGE: t('admin.authorizations.resourceTypes.MESSAGE'),
		PROBE: t('admin.authorizations.resourceTypes.PROBE'),
		PROCESS_DEFINITION: t('admin.authorizations.resourceTypes.PROCESS_DEFINITION'),
		RESOURCE: t('admin.authorizations.resourceTypes.RESOURCE'),
		ROLE: t('admin.authorizations.resourceTypes.ROLE'),
		SECRET: t('admin.authorizations.resourceTypes.SECRET'),
		SYSTEM: t('admin.authorizations.resourceTypes.SYSTEM'),
		TENANT: t('admin.authorizations.resourceTypes.TENANT'),
		USER: t('admin.authorizations.resourceTypes.USER'),
		USER_TASK: t('admin.authorizations.resourceTypes.USER_TASK'),
	};
	return labels[resourceType];
}

export {getResourceTypeLabel};
