/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {auditLogEntityTypeSchema, auditLogOperationTypeSchema} from '@camunda/camunda-api-zod-schemas/8.11';
import {commaSeparated} from '#/operate/pages/OperationsLog/operationsLog.schema';

const processInstanceOperationsLogSearchSchema = z.object({
	operationType: commaSeparated(auditLogOperationTypeSchema).catch(undefined),
	entityType: commaSeparated(auditLogEntityTypeSchema).catch(undefined),
	sort: z.string().optional(),
});

type ProcessInstanceOperationsLogSearch = z.infer<typeof processInstanceOperationsLogSearchSchema>;

export {processInstanceOperationsLogSearchSchema};
export type {ProcessInstanceOperationsLogSearch};
