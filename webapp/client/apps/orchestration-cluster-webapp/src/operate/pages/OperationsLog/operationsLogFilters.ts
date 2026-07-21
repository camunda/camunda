/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AuditLogEntityType, AuditLogOperationType} from '@camunda/camunda-api-zod-schemas/8.10';

const AUDIT_LOG_ENTITY_TYPE_FILTER_VALUES: AuditLogEntityType[] = [
	'USER_TASK',
	'BATCH',
	'RESOURCE',
	'CLIENT',
	'DECISION',
	'INCIDENT',
	'JOB',
	'PROCESS_INSTANCE',
	'VARIABLE',
];

const AUDIT_LOG_OPERATION_TYPE_FILTER_VALUES: AuditLogOperationType[] = [
	'CREATE',
	'UPDATE',
	'DELETE',
	'COMPLETE',
	'EVALUATE',
	'ASSIGN',
	'CANCEL',
	'MIGRATE',
	'MODIFY',
	'RESOLVE',
	'RESUME',
	'SUSPEND',
	'UNASSIGN',
];

export {AUDIT_LOG_ENTITY_TYPE_FILTER_VALUES, AUDIT_LOG_OPERATION_TYPE_FILTER_VALUES};
