/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.10';
import {z} from 'zod';

const processInstanceSelectionSchema = z.object({
	elementId: z.coerce.string().optional(),
	elementInstanceKey: z.coerce.string().optional(),
	isMultiInstanceBody: z.boolean().optional(),
	isPlaceholder: z.boolean().optional(),
	anchorElementId: z.coerce.string().optional(),
});

const processInstanceSearchSchema: z.ZodType<
	ProcessInstanceSelection,
	z.input<typeof processInstanceSelectionSchema>
> = processInstanceSelectionSchema.loose();

type ProcessInstanceSearch = ProcessInstanceSelection & Record<string, unknown>;
type ProcessInstanceSelection = z.infer<typeof processInstanceSelectionSchema>;
type ProcessInstanceTab = 'details' | 'incidents' | 'variables';
type ProcessInstanceTabPath =
	| '/operate/processes/$processInstanceId/details'
	| '/operate/processes/$processInstanceId/incidents'
	| '/operate/processes/$processInstanceId/variables';

type GetDefaultProcessInstanceTabOptions = {
	isProcessLevelWaiting?: boolean;
};

function hasProcessInstanceSelection({elementId, elementInstanceKey}: ProcessInstanceSelection): boolean {
	return Boolean(elementId || elementInstanceKey);
}

function getDefaultProcessInstanceTab(
	processInstance: Pick<ProcessInstance, 'hasIncident'>,
	selection: ProcessInstanceSelection,
	options: GetDefaultProcessInstanceTabOptions = {},
): ProcessInstanceTab {
	if (processInstance.hasIncident) {
		return 'incidents';
	}

	return hasProcessInstanceSelection(selection) || options.isProcessLevelWaiting === true ? 'details' : 'variables';
}

function getProcessInstanceTabPath(tab: ProcessInstanceTab): ProcessInstanceTabPath {
	switch (tab) {
		case 'details':
			return '/operate/processes/$processInstanceId/details';
		case 'incidents':
			return '/operate/processes/$processInstanceId/incidents';
		case 'variables':
		default:
			return '/operate/processes/$processInstanceId/variables';
	}
}

export {
	processInstanceSearchSchema,
	hasProcessInstanceSelection,
	getDefaultProcessInstanceTab,
	getProcessInstanceTabPath,
};
export type {ProcessInstanceSearch, ProcessInstanceSelection};
