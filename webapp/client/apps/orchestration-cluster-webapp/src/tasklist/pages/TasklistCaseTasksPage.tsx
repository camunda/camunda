/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import type {CaseData} from '#/tasklist/modules/case-details/caseData';
import {CaseWorkCard} from '#/tasklist/modules/case-details/components/CaseWorkCard';
import {getCaseView} from '#/tasklist/modules/case-details/getCaseView';
import {useCaseWork} from '#/tasklist/modules/case-details/useCaseWork';

// Activities that create user tasks: the case worker opens the task after the activation.
const ACTIVITIES_WITH_TASKS = new Set(['userTask', 'subProcess', 'adHocSubProcess']);

type Props = CaseData & {
	onOpenTask: (userTaskKey: string) => void;
};

const TasklistCaseTasksPage: React.FC<Props> = ({
	onOpenTask,
	currentUser,
	processInstance,
	xml,
	elementInstances,
	userTasks,
	rootVariables,
}) => {
	const {model, currentStage, work} = useMemo(
		() => getCaseView({processInstance, xml, elementInstances, userTasks, rootVariables}),
		[processInstance, xml, elementInstances, userTasks, rootVariables],
	);
	const stageInstanceKey = currentStage?.elementInstanceKey ?? null;
	const caseWork = useCaseWork({caseRef: processInstance, onOpenTask});

	return (
		<CaseWorkCard
			caseState={processInstance.state}
			stageName={currentStage?.stage.name ?? null}
			isStageBlocked={currentStage?.state === 'incident'}
			work={work}
			currentUsername={currentUser.username}
			caseWorkStatus={caseWork.status}
			openingElementId={caseWork.openingElementId}
			onOpenTask={onOpenTask}
			onOpenActivity={(elementId) => {
				if (stageInstanceKey !== null) {
					caseWork.openActivity({
						stageInstanceKey,
						elementId,
						expectsUserTask: ACTIVITIES_WITH_TASKS.has(model.elementTypes.get(elementId) ?? ''),
						knownTaskKeys: userTasks.map(({userTaskKey}) => userTaskKey),
						knownElementInstanceKeys: elementInstances.map(({elementInstanceKey}) => elementInstanceKey),
					});
				}
			}}
			onCompleteStage={() => {
				if (work.completion !== null && stageInstanceKey !== null) {
					caseWork.completeStage({
						stageInstanceKey,
						stageIds: model.stages.map(({id}) => id),
						completion: work.completion,
					});
				}
			}}
		/>
	);
};

export {TasklistCaseTasksPage};
