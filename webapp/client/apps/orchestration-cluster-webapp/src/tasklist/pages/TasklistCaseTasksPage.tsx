/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {toast} from '@camunda/design-system';
import type {CaseData} from '#/tasklist/modules/case-details/caseData';
import {CaseWorkCard} from '#/tasklist/modules/case-details/components/CaseWorkCard';
import {getCaseView} from '#/tasklist/modules/case-details/getCaseView';
import {useActivateActivity} from '#/tasklist/modules/case-details/useCaseActions';
import {useStageCompletion} from '#/tasklist/modules/case-details/useStageCompletion';

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
	const {t} = useTranslation();
	const {model, currentStage, work} = useMemo(
		() => getCaseView({processInstance, xml, elementInstances, userTasks, rootVariables}),
		[processInstance, xml, elementInstances, userTasks, rootVariables],
	);
	const stageInstanceKey = currentStage?.elementInstanceKey ?? null;
	const activateActivity = useActivateActivity(stageInstanceKey);
	const stageCompletion = useStageCompletion(processInstance);

	return (
		<CaseWorkCard
			stageName={currentStage?.stage.name ?? null}
			work={work}
			currentUsername={currentUser.username}
			startingElementId={activateActivity.isPending ? (activateActivity.variables ?? null) : null}
			stageCompletionStatus={stageCompletion.status}
			closingStageName={stageCompletion.closingStageName}
			onOpenTask={onOpenTask}
			onStartActivity={(elementId) =>
				activateActivity.mutate(elementId, {
					onError: () => toast.error(t('tasklist.caseActivityStartFailed')),
				})
			}
			onCompleteStage={() => {
				if (work.completion !== null && currentStage !== null && stageInstanceKey !== null) {
					stageCompletion.completeStage({
						stageName: currentStage.stage.name,
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
