/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {TFunction} from 'i18next';
import type {AuditLog, AuditLogOperationType} from '@camunda/camunda-api-zod-schemas/8.10/audit-log';
import type {BatchOperationType} from '@camunda/camunda-api-zod-schemas/8.10';
import {spaceAndCapitalize} from '#/operate/shared/utils/spaceAndCapitalize';

const INVALID_PROCESS_INSTANCE_KEY = '-1';

function formatBatchTitle(t: TFunction, batchOperationType?: BatchOperationType): string | undefined {
	switch (batchOperationType) {
		case 'DELETE_PROCESS_INSTANCE':
		case 'CANCEL_PROCESS_INSTANCE':
		case 'MIGRATE_PROCESS_INSTANCE':
		case 'MODIFY_PROCESS_INSTANCE':
			return t('operate.operationsLog.batchTitle.processInstances');
		case 'RESOLVE_INCIDENT':
			return t('operate.operationsLog.batchTitle.incidents');
		case 'DELETE_DECISION_DEFINITION':
			return t('operate.operationsLog.batchTitle.decisions');
		case 'DELETE_PROCESS_DEFINITION':
			return t('operate.operationsLog.batchTitle.processDefinitions');
		case 'ADD_VARIABLE':
		case 'UPDATE_VARIABLE':
			return t('operate.operationsLog.batchTitle.variables');
		default:
			return undefined;
	}
}

function formatModalHeading(auditLog: AuditLog): string {
	return `${spaceAndCapitalize(auditLog.operationType)} ${spaceAndCapitalize(auditLog.entityType)}`;
}

function hasActorIcon(auditLog: AuditLog): boolean {
	return auditLog.actorType === 'USER' || auditLog.actorType === 'CLIENT';
}

function isValidProcessInstanceKey(processInstanceKey?: string | null): processInstanceKey is string {
	return Boolean(processInstanceKey) && processInstanceKey !== INVALID_PROCESS_INSTANCE_KEY;
}

type EntityKeyData = {
	name?: string | null;
	link?: string;
	linkLabel?: string;
	label?: string | null;
};

/**
 * Maps an audit log entry to its entity-key link. `link` is a plain `href` for
 * PROCESS_INSTANCE, DECISION and BATCH entity types because their detail pages are not
 * migrated to the unified webapp yet (tracked separately from this page's migration).
 */
function mapToCellEntityKeyData(
	t: TFunction,
	item: AuditLog,
	processDefinitionName?: string | null,
	decisionDefinitionName?: string | null,
): EntityKeyData {
	switch (item.entityType) {
		case 'BATCH':
			return {
				link: `/operate/batch-operations/${item.batchOperationKey}`,
				linkLabel: t('operate.operationsLog.entityLinks.viewBatchOperation', {key: item.batchOperationKey}),
				label: item.batchOperationKey,
			};
		case 'PROCESS_INSTANCE':
			return {
				name: processDefinitionName,
				link: `/operate/processes/${item.entityKey}`,
				linkLabel: t('operate.operationsLog.entityLinks.viewProcessInstance', {key: item.entityKey}),
				label: item.entityKey,
			};
		case 'DECISION':
			if (item.operationType === 'EVALUATE') {
				return {
					name: decisionDefinitionName,
					link: `/operate/decisions/${item.entityKey}`,
					linkLabel: t('operate.operationsLog.entityLinks.viewDecisionInstance', {key: item.entityKey}),
					label: item.entityKey,
				};
			}
			return {name: decisionDefinitionName, label: item.entityKey};
		case 'USER_TASK':
			return {
				linkLabel: t('operate.operationsLog.entityLinks.viewUserTask', {key: item.entityKey}),
				label: item.entityKey,
			};
		default:
			return {
				label: item.entityKey,
			};
	}
}

const USER_TASK_OPERATIONS: Set<AuditLogOperationType> = new Set(['ASSIGN', 'UNASSIGN']);

type CellDetailsData = {property?: string; value?: string | null};

function mapToCellDetailsData(t: TFunction, item: AuditLog): CellDetailsData {
	if (item.entityType === 'BATCH' && item.batchOperationType) {
		return {
			property: t('operate.operationsLog.details.batchOperationType'),
			value: spaceAndCapitalize(item.batchOperationType),
		};
	}
	if (item.entityType === 'USER_TASK' && USER_TASK_OPERATIONS.has(item.operationType)) {
		return {
			property: t('operate.operationsLog.details.assignee'),
			value: item.relatedEntityKey,
		};
	}
	if (item.entityType === 'RESOURCE' && item.resourceKey) {
		return {
			property: t('operate.operationsLog.details.resourceKey'),
			value: item.resourceKey,
		};
	}
	return {};
}

export {
	formatBatchTitle,
	formatModalHeading,
	hasActorIcon,
	isValidProcessInstanceKey,
	mapToCellEntityKeyData,
	mapToCellDetailsData,
};
