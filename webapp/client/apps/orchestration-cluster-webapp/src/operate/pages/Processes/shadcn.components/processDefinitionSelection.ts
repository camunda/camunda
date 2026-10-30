/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.11';

type ProcessDefinitionSelection =
	| {kind: 'no-match'}
	| {kind: 'single-version'; definition: ProcessDefinition}
	| {kind: 'all-versions'; definition: Pick<ProcessDefinition, 'name' | 'processDefinitionId'>}
	| {kind: 'multiple-tenants'; definition: Pick<ProcessDefinition, 'name' | 'processDefinitionId'>};

export type {ProcessDefinitionSelection};
