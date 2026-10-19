/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {
	CASE_HEADER_VARIABLE,
	getCaseHeader,
	getCaseProgress,
	getCaseTitle,
	getCurrentStage,
	getDocumentReferences,
	parseCaseModel,
} from '@camunda/case-management-sdk';
import {Card, CardContent, CardHeader, CardTitle, PageLayout, toast} from '@camunda/design-system';
import type {
	CurrentUser,
	ElementInstance,
	ProcessInstance,
	UserTask,
	Variable,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {CaseDetailsCard} from '#/tasklist/modules/case-details/components/CaseDetailsCard';
import {CaseDiagramCard} from '#/tasklist/modules/case-details/components/CaseDiagramCard';
import {CaseDocumentsCard} from '#/tasklist/modules/case-details/components/CaseDocumentsCard';
import {CaseHeader} from '#/tasklist/modules/case-details/components/CaseHeader';
import {CaseIncidentBanner} from '#/tasklist/modules/case-details/components/CaseIncidentBanner';
import {CaseTaskPanel} from '#/tasklist/modules/case-details/components/CaseTaskPanel';
import {CaseWorkCard} from '#/tasklist/modules/case-details/components/CaseWorkCard';
import {StageLine} from '#/tasklist/modules/case-details/components/StageLine';
import {getStageWork} from '#/tasklist/modules/case-details/getStageWork';
import {useActivateActivity, useCompleteStage} from '#/tasklist/modules/case-details/useCaseActions';
import {extractVariablesFromFormSchema} from '#/tasklist/modules/form-js/extractVariablesFromFormSchema';

type SelectedTask = {
	task: UserTask;
	formSchema: string | null;
	variables: Variable[];
};

type Props = {
	processInstance: ProcessInstance;
	xml: string;
	elementInstances: ElementInstance[];
	userTasks: UserTask[];
	rootVariables: Variable[];
	currentUser: CurrentUser;
	caseFormSchema: string | null;
	selectedTask: SelectedTask | null;
	isSearchingNextWork: boolean;
	onOpenTask: (userTaskKey: string) => void;
	onCloseTask: () => void;
	onTaskCompleted: (userTaskKey: string) => void;
};

const TasklistCaseDetailsPage: React.FC<Props> = ({
	processInstance,
	xml,
	elementInstances,
	userTasks,
	rootVariables,
	currentUser,
	caseFormSchema,
	selectedTask,
	isSearchingNextWork,
	onOpenTask,
	onCloseTask,
	onTaskCompleted,
}) => {
	const {t} = useTranslation();
	const model = useMemo(
		() => parseCaseModel(xml, processInstance.processDefinitionId),
		[xml, processInstance.processDefinitionId],
	);
	// A case with a call activity is outside the case contract and gets the simplified view (CASE_IDENTITY.md).
	const isSimplifiedView = model.caseMarker !== null && model.hasCallActivity;
	const progress = useMemo(
		() => (isSimplifiedView ? [] : getCaseProgress(model, elementInstances, processInstance.state)),
		[isSimplifiedView, model, elementInstances, processInstance.state],
	);
	const currentStage = getCurrentStage(progress);
	const work = useMemo(
		() => getStageWork({model, currentStage, elementInstances, userTasks, rootVariables}),
		[model, currentStage, elementInstances, userTasks, rootVariables],
	);
	const processName = processInstance.processDefinitionName || processInstance.processDefinitionId;
	const title = getCaseTitle(rootVariables.find(({name}) => name === model.caseMarker?.titleVariable)) ?? processName;
	const header = getCaseHeader(rootVariables.find(({name}) => name === CASE_HEADER_VARIABLE)) ?? [];
	const documents = useMemo(() => getDocumentReferences(rootVariables), [rootVariables]);
	const caseFormVariables = useMemo(() => {
		const names = new Set(caseFormSchema === null ? [] : extractVariablesFromFormSchema(caseFormSchema));
		return rootVariables.filter(({name}) => names.has(name));
	}, [caseFormSchema, rootVariables]);
	const stageInstanceKey = currentStage?.elementInstanceKey ?? null;
	const activateActivity = useActivateActivity(stageInstanceKey);
	const completeStage = useCompleteStage(stageInstanceKey, processInstance.tenantId);

	return (
		<PageLayout>
			<div className="flex flex-col gap-4">
				<CaseHeader
					title={title}
					processName={processName}
					caseId={processInstance.businessId ?? processInstance.processInstanceKey}
					hasIncident={processInstance.hasIncident}
					startDate={processInstance.startDate}
					header={isSimplifiedView ? [] : header}
				/>

				{processInstance.hasIncident ? <CaseIncidentBanner /> : null}

				{isSimplifiedView ? <CaseDiagramCard xml={xml} elementInstances={elementInstances} /> : null}

				{progress.length === 0 ? null : (
					<Card>
						<CardHeader>
							<CardTitle>{t('tasklist.caseProgressTitle')}</CardTitle>
						</CardHeader>
						<CardContent>
							<StageLine progress={progress} />
						</CardContent>
					</Card>
				)}

				<div className="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_24rem]">
					<div className="flex min-w-0 flex-col gap-4">
						{selectedTask === null ? null : (
							<CaseTaskPanel
								key={selectedTask.task.userTaskKey}
								task={selectedTask.task}
								currentUser={currentUser}
								formSchema={selectedTask.formSchema}
								variables={selectedTask.variables}
								onCompleted={onTaskCompleted}
								onClose={onCloseTask}
							/>
						)}
						<CaseWorkCard
							stageName={currentStage?.stage.name ?? null}
							work={work}
							currentUsername={currentUser.username}
							selectedTaskKey={selectedTask?.task.userTaskKey ?? null}
							isSearchingNextWork={isSearchingNextWork}
							startingElementId={activateActivity.isPending ? (activateActivity.variables ?? null) : null}
							isCompletingStage={completeStage.isPending}
							onOpenTask={onOpenTask}
							onStartActivity={(elementId) =>
								activateActivity.mutate(elementId, {
									onError: () => toast.error(t('tasklist.caseActivityStartFailed')),
								})
							}
							onCompleteStage={() => {
								if (work.completion !== null) {
									completeStage.mutate(work.completion, {
										onError: () => toast.error(t('tasklist.caseStageDoneFailed')),
									});
								}
							}}
						/>
						{caseFormSchema === null || isSimplifiedView ? null : (
							<CaseDetailsCard formSchema={caseFormSchema} variables={caseFormVariables} />
						)}
					</div>
					<div className="flex min-w-0 flex-col gap-4">
						<CaseDocumentsCard documents={documents} />
					</div>
				</div>
			</div>
		</PageLayout>
	);
};

export {TasklistCaseDetailsPage};
