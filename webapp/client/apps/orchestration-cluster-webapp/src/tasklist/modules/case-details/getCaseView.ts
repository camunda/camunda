/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getCaseProgress, getCurrentStage, parseCaseModel} from '@camunda/case-management-sdk';
import type {CaseData} from '#/tasklist/modules/case-details/caseData';
import {getStageWork} from '#/tasklist/modules/case-details/getStageWork';

/** Derives the state of a case from the BPMN and the existing APIs (CASE_UI.md). */
function getCaseView({
	processInstance,
	xml,
	elementInstances,
	userTasks,
	caseVariables,
}: Pick<CaseData, 'processInstance' | 'xml' | 'elementInstances' | 'userTasks' | 'caseVariables'>) {
	const model = parseCaseModel(xml, processInstance.processDefinitionId);
	// A case with a call activity is outside the case contract and gets the simplified view (CASE_IDENTITY.md).
	const isSimplifiedView = model.caseMarker !== null && model.hasCallActivity;
	const progress = isSimplifiedView ? [] : getCaseProgress(model, elementInstances, processInstance.state);
	const currentStage = getCurrentStage(progress);
	const work = getStageWork({model, currentStage, elementInstances, userTasks, caseVariables});

	return {model, isSimplifiedView, progress, currentStage, work};
}

export {getCaseView};
