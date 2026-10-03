/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {PageLayout} from '@camunda/design-system';
import type {
	ElementInstance,
	ElementInstanceInspection,
	Incident,
	ProcessDefinitionStatistic,
	ProcessInstance,
	UserTask,
	Variable,
} from '@camunda/camunda-api-zod-schemas/8.11';
import type {CaseMetadata} from '#/tasklist/modules/case-details/caseMetadataSchema';
import {CaseDocumentsCard} from '#/tasklist/modules/case-details/components/CaseDocumentsCard';
import {CaseFormCard} from '#/tasklist/modules/case-details/components/CaseFormCard';
import {CaseHeader} from '#/tasklist/modules/case-details/components/CaseHeader';
import {CaseHistory} from '#/tasklist/modules/case-details/components/CaseHistory';
import {CaseProgressCard} from '#/tasklist/modules/case-details/components/CaseProgressCard';
import {CaseTasksCard} from '#/tasklist/modules/case-details/components/CaseTasksCard';
import {getCaseDocuments} from '#/tasklist/modules/case-details/getCaseDocuments';
import {getCaseHistory} from '#/tasklist/modules/case-details/getCaseHistory';
import {getCaseWaits} from '#/tasklist/modules/case-details/getCaseWaits';
import {getMilestoneProgress} from '#/tasklist/modules/case-details/getMilestoneProgress';
import {parseCaseModel} from '#/tasklist/modules/case-details/parseCaseModel';
import type {ProgressView} from '#/tasklist/modules/case-details/searchSchema';
import {mapProcessInstanceToCase} from '#/tasklist/modules/cases/mapProcessInstanceToCase';

type Props = {
	processInstance: ProcessInstance;
	metadata: CaseMetadata | null;
	xml: string;
	statistics: ProcessDefinitionStatistic[];
	elementInstances: ElementInstance[];
	waitStates: ElementInstanceInspection[];
	userTasks: UserTask[];
	incidents: Incident[];
	documentVariables: Variable[];
	formSchema: string | null;
	formVariables: Variable[];
	currentUsername: string;
	progressView: ProgressView;
};

const TasklistCaseDetailsPage: React.FC<Props> = ({
	processInstance,
	metadata,
	xml,
	statistics,
	elementInstances,
	waitStates,
	userTasks,
	incidents,
	documentVariables,
	formSchema,
	formVariables,
	currentUsername,
	progressView,
}) => {
	const caseItem = useMemo(() => mapProcessInstanceToCase(processInstance), [processInstance]);
	const caseModel = useMemo(
		() => parseCaseModel(xml, processInstance.processDefinitionId),
		[xml, processInstance.processDefinitionId],
	);
	const milestones = useMemo(
		() => getMilestoneProgress(caseModel.milestones, statistics, elementInstances, processInstance.state),
		[caseModel.milestones, statistics, elementInstances, processInstance.state],
	);
	const waits = useMemo(
		() =>
			getCaseWaits({
				waitStates,
				userTasks,
				elementInstances,
				elementNames: caseModel.elementNames,
				currentUsername,
			}),
		[waitStates, userTasks, elementInstances, caseModel.elementNames, currentUsername],
	);
	const history = useMemo(
		() => getCaseHistory({elementInstances, userTasks, incidents, elementNames: caseModel.elementNames}),
		[elementInstances, userTasks, incidents, caseModel.elementNames],
	);
	const documents = useMemo(() => getCaseDocuments(documentVariables), [documentVariables]);

	return (
		<PageLayout>
			<div className="flex flex-col gap-4">
				<CaseHeader caseItem={caseItem} header={metadata?.header ?? []} />
				<CaseProgressCard
					milestones={milestones}
					waits={waits}
					xml={xml}
					statistics={statistics}
					progressView={progressView}
				/>
				<div className="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_24rem]">
					<div className="flex min-w-0 flex-col gap-4">
						<CaseTasksCard userTasks={userTasks} currentUsername={currentUsername} />
						<CaseFormCard formSchema={formSchema} variables={formVariables} />
					</div>
					<div className="flex min-w-0 flex-col gap-4">
						<CaseDocumentsCard documents={documents} />
						<CaseHistory entries={history} />
					</div>
				</div>
			</div>
		</PageLayout>
	);
};

export {TasklistCaseDetailsPage};
