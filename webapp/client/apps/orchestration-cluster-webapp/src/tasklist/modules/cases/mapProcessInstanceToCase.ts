/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {CASE_PREFIX} from '#/tasklist/modules/cases/caseId';

type CaseStatus = 'active' | 'incident';

type Case = {
	processInstanceKey: string;
	caseId: string;
	title: string;
	startDate: string;
	status: CaseStatus;
};

function mapProcessInstanceToCase(processInstance: ProcessInstance): Case {
	return {
		processInstanceKey: processInstance.processInstanceKey,
		// The cases filter only returns business IDs that start with the case prefix.
		caseId: processInstance.businessId?.slice(CASE_PREFIX.length) ?? processInstance.processInstanceKey,
		title: processInstance.processDefinitionName || processInstance.processDefinitionId,
		startDate: processInstance.startDate,
		status: processInstance.hasIncident ? 'incident' : 'active',
	};
}

export {mapProcessInstanceToCase};
export type {Case, CaseStatus};
