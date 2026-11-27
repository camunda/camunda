/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {Link as RouterLink} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {Alert, EmptyState, Heading, Link, linkVariants, Skeleton} from '@camunda/design-system';
import {MousePointerClick} from '@camunda/design-system/icons';
import type {
	ProcessInstance,
	QueryDecisionInstancesResponseBody,
	QueryProcessInstancesResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {getBootConfig} from '#/shared/config/getBootConfig';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {mergePathname} from '#/shared/http/mergePathname';
import {OPERATE_BASE} from '#/shared/operateBase';
import {CenteredContent} from '#/operate/shared/CenteredContent/shadcn.components/CenteredContent';
import {StructuredList} from '#/operate/shared/StructuredList/shadcn.components/StructuredList';
import {useDiagramXml} from '#/operate/pages/Processes/useDiagramXml';
import {isCamundaUserTask, isMessageEventElement} from '#/operate/shared/utils/elements';
import {isInstanceRunning} from '#/operate/shared/utils/processInstance';
import {
	calledDecisionInstanceQuery,
	calledProcessInstancesQuery,
	elementJobQuery,
	elementMessageSubscriptionQuery,
	elementUserTaskQuery,
	waitStatesQuery,
} from '../details.queries';
import {getExecutionDuration} from '../getExecutionDuration';
import {useProcessInstanceElementSelection} from '../useProcessInstanceElementSelection';
import {useProcessInstancePage} from '../useProcessInstancePage';
import {DataLoadErrorAlert} from '#/operate/shared/DataLoadErrorAlert/shadcn.components/DataLoadErrorAlert';
import {ProcessInstanceWaitingStatus} from './ProcessInstanceWaitingStatus';

type Row = {key: string; label: string; value: React.ReactNode};

const SKELETON_ROW_COUNT = 5;

const CalledProcessInstanceValue: React.FC<{
	calledProcessInstances: QueryProcessInstancesResponseBody | undefined;
	processInstance: ProcessInstance;
	isMultiTenancyEnabled: boolean;
}> = ({calledProcessInstances, processInstance, isMultiTenancyEnabled}) => {
	const {t} = useTranslation();
	const count = calledProcessInstances?.page.totalItems ?? 0;
	const calledProcessInstance = count === 1 ? calledProcessInstances?.items[0] : undefined;

	if (count > 1) {
		return (
			<RouterLink
				to={`${OPERATE_BASE}/processes` as const}
				search={{
					parentProcessInstanceKey: processInstance.processInstanceKey,
					active: true,
					suspended: true,
					incidents: true,
					completed: true,
					canceled: true,
					...(isMultiTenancyEnabled ? {tenantId: processInstance.tenantId} : {}),
				}}
				title={t('operate.processInstance.details.viewAllCalled')}
				aria-label={t('operate.processInstance.details.viewAllCalled')}
				className={linkVariants()}
			>
				{t('operate.processInstance.details.viewAllCalledCount', {count})}
			</RouterLink>
		);
	}

	if (calledProcessInstance !== undefined) {
		const name = calledProcessInstance.processDefinitionName ?? calledProcessInstance.processDefinitionId;
		const label = t('operate.processInstance.details.viewInstance', {
			name,
			key: calledProcessInstance.processInstanceKey,
		});
		return (
			<RouterLink
				to={`${OPERATE_BASE}/processes/$processInstanceId` as const}
				params={{processInstanceId: calledProcessInstance.processInstanceKey}}
				title={label}
				aria-label={label}
				className={linkVariants()}
			>
				{`${name} - ${calledProcessInstance.processInstanceKey}`}
			</RouterLink>
		);
	}

	return count === 0 ? t('operate.processInstance.details.none') : null;
};

const CalledDecisionInstanceValue: React.FC<{
	calledDecisionInstance: QueryDecisionInstancesResponseBody['items'][number] | null | undefined;
}> = ({calledDecisionInstance}) => {
	const {t} = useTranslation();

	if (!calledDecisionInstance) {
		return '-';
	}

	const label = t('operate.processInstance.details.viewInstance', {
		name: calledDecisionInstance.decisionDefinitionName,
		key: calledDecisionInstance.decisionEvaluationInstanceKey,
	});
	return (
		<RouterLink
			to={`${OPERATE_BASE}/decisions/$decisionInstanceId` as const}
			params={{decisionInstanceId: calledDecisionInstance.decisionEvaluationInstanceKey}}
			title={label}
			aria-label={label}
			className={linkVariants()}
		>
			{`${calledDecisionInstance.decisionDefinitionName} - ${calledDecisionInstance.decisionEvaluationInstanceKey}`}
		</RouterLink>
	);
};

const ElementInstanceSkeleton: React.FC = () => (
	<div className="flex flex-col gap-3" aria-hidden>
		{Array.from({length: SKELETON_ROW_COUNT}, (_, index) => (
			<Skeleton key={index} className="h-4 w-full" />
		))}
	</div>
);

const ProcessInstanceDetails: React.FC = () => {
	const {t} = useTranslation();
	const {processInstanceId, processInstance} = useProcessInstancePage();
	const {
		hasSelection,
		resolvedElementInstance,
		selectedElementId,
		selectedElementInstanceCount,
		anchorElementId,
		isFetchingElement,
		isFetchingElementError,
		refetchElement,
	} = useProcessInstanceElementSelection();
	const {data: diagram} = useDiagramXml(processInstance.processDefinitionKey);

	const isProcessScope = !hasSelection;
	const isRunning = isInstanceRunning(processInstance);
	const effectiveElementId = anchorElementId ?? selectedElementId;
	const businessObject = diagram?.businessObjects[effectiveElementId ?? ''];
	const elementInstanceKey = resolvedElementInstance?.elementInstanceKey;
	const resolvedElementType = resolvedElementInstance?.type;

	const waitStatesKey = isProcessScope ? processInstanceId : elementInstanceKey;
	const isWaitStatesEnabled =
		getClientConfig().deployment.isWaitStatesEnabled &&
		waitStatesKey !== undefined &&
		isRunning &&
		(isProcessScope || resolvedElementInstance?.state === 'ACTIVE');
	const {data: waitStatesData} = useQuery({
		...waitStatesQuery({processInstanceKey: processInstanceId, elementInstanceKey: waitStatesKey ?? ''}),
		enabled: isWaitStatesEnabled,
	});
	const waitStates = isWaitStatesEnabled ? (waitStatesData ?? []) : [];

	const isCallActivity = businessObject?.$type === 'bpmn:CallActivity';
	const isBusinessRuleTask = businessObject?.$type === 'bpmn:BusinessRuleTask';
	const isCamundaTask = resolvedElementType === 'USER_TASK' && isCamundaUserTask(businessObject);
	const {components, deployment} = getClientConfig();
	const isTasklistActive = components.active.includes('tasklist');
	const isMultiTenancyEnabled = deployment.isMultiTenancyEnabled;
	const isMessageElement = isMessageEventElement(businessObject);
	const key = elementInstanceKey ?? '';

	const {data: calledProcessInstances} = useQuery({
		...calledProcessInstancesQuery(key),
		enabled: key !== '' && resolvedElementType === 'CALL_ACTIVITY',
	});
	const {data: job} = useQuery({...elementJobQuery(key), enabled: key !== ''});
	const {data: calledDecisionInstance} = useQuery({
		...calledDecisionInstanceQuery(key),
		enabled: key !== '' && resolvedElementType === 'BUSINESS_RULE_TASK',
	});
	const {data: userTask} = useQuery({...elementUserTaskQuery(key), enabled: key !== '' && isCamundaTask});
	const {data: messageSubscription} = useQuery({
		...elementMessageSubscriptionQuery(key),
		enabled: key !== '' && isMessageElement,
	});

	if (isProcessScope) {
		return (
			<div
				role="region"
				aria-label={t('operate.processInstance.details.title')}
				className="flex h-full min-h-0 flex-col gap-4 overflow-y-auto px-4 pb-4"
			>
				<ProcessInstanceWaitingStatus waitStates={waitStates} />
			</div>
		);
	}

	const hasMultipleInstances = selectedElementInstanceCount !== null && selectedElementInstanceCount > 1;

	if (isFetchingElementError) {
		return (
			<div role="region" aria-label={t('operate.processInstance.details.title')} className="flex h-full min-h-0">
				<DataLoadErrorAlert className="pt-0" onRetry={() => void refetchElement()} />
			</div>
		);
	}

	if (resolvedElementInstance === null && !isFetchingElement) {
		return (
			<div role="region" aria-label={t('operate.processInstance.details.title')} className="flex h-full min-h-0">
				<CenteredContent className="flex-1">
					<EmptyState
						size="sm"
						headingLevel={3}
						icon={<MousePointerClick aria-hidden />}
						heading={t(
							hasMultipleInstances
								? 'operate.processInstance.ds.operationsLog.multiInstanceHeading'
								: 'operate.processInstance.ds.details.noElementHeading',
						)}
						description={t(
							hasMultipleInstances
								? 'operate.processInstance.ds.details.multiInstanceDescription'
								: 'operate.processInstance.ds.details.noElementDescription',
						)}
					/>
				</CenteredContent>
			</div>
		);
	}

	const tasklistFilter = userTask?.state === 'COMPLETED' ? 'completed' : 'all-open';
	const withDash = (value: string | null | undefined) => value ?? '-';
	const joinOrDash = (values: string[]) => (values.length > 0 ? values.join(', ') : '-');

	const rows: Row[] = resolvedElementInstance
		? [
				{
					key: 'element-instance-key',
					label: t('operate.processInstance.details.elementInstanceKey'),
					value: resolvedElementInstance.elementInstanceKey,
				},
				...(isCamundaTask && userTask && isTasklistActive
					? [
							{
								key: 'open-tasklist',
								label: t('operate.processInstance.details.tasklist'),
								value: (
									<Link
										href={`${mergePathname(getBootConfig().contextPath, `/tasklist/${userTask.userTaskKey}`)}?filter=${tasklistFilter}`}
										target="_blank"
										rel="noopener noreferrer"
									>
										{t('operate.processInstance.details.openTasklist')}
									</Link>
								),
							},
						]
					: []),
				{
					key: 'execution-duration',
					label: t('operate.processInstance.details.executionDuration'),
					value: resolvedElementInstance.startDate
						? getExecutionDuration(resolvedElementInstance.startDate, resolvedElementInstance.endDate)
						: '-',
				},
				...(job?.retries === undefined
					? []
					: [
							{
								key: 'retries-left',
								label: t('operate.processInstance.details.retriesLeft'),
								value: <span data-testid="retries-left-count">{job.retries}</span>,
							},
						]),
				...(job?.priority === undefined
					? []
					: [
							{
								key: 'job-priority',
								label: t('operate.processInstance.details.jobPriority'),
								value: <span data-testid="job-priority">{job.priority}</span>,
							},
						]),
				...(job?.type === undefined
					? []
					: [{key: 'job-type', label: t('operate.processInstance.details.jobType'), value: job.type}]),
				...(job?.worker === undefined
					? []
					: [{key: 'worker', label: t('operate.processInstance.details.worker'), value: job.worker}]),
				...(isCallActivity
					? [
							{
								key: 'called-process-instance',
								label: t('operate.processInstance.details.calledProcessInstance'),
								value: (
									<CalledProcessInstanceValue
										calledProcessInstances={calledProcessInstances}
										processInstance={processInstance}
										isMultiTenancyEnabled={isMultiTenancyEnabled}
									/>
								),
							},
						]
					: []),
				...(isBusinessRuleTask
					? [
							{
								key: 'called-decision-instance',
								label: t('operate.processInstance.details.calledDecisionInstance'),
								value: <CalledDecisionInstanceValue calledDecisionInstance={calledDecisionInstance} />,
							},
						]
					: []),
				...(isCamundaTask && userTask
					? [
							{
								key: 'assignee',
								label: t('operate.processInstance.details.assignee'),
								value: withDash(userTask.assignee),
							},
							{
								key: 'candidate-users',
								label: t('operate.processInstance.details.candidateUsers'),
								value: joinOrDash(userTask.candidateUsers),
							},
							{
								key: 'candidate-groups',
								label: t('operate.processInstance.details.candidateGroups'),
								value: joinOrDash(userTask.candidateGroups),
							},
							{key: 'due-date', label: t('operate.processInstance.details.dueDate'), value: withDash(userTask.dueDate)},
							{
								key: 'follow-up-date',
								label: t('operate.processInstance.details.followUpDate'),
								value: withDash(userTask.followUpDate),
							},
							{key: 'priority', label: t('operate.processInstance.details.priority'), value: String(userTask.priority)},
							{key: 'form-key', label: t('operate.processInstance.details.formKey'), value: withDash(userTask.formKey)},
						]
					: []),
				...(isMessageElement && messageSubscription
					? [
							{
								key: 'message-name',
								label: t('operate.processInstance.details.messageName'),
								value: withDash(messageSubscription.messageName),
							},
							{
								key: 'correlation-key',
								label: t('operate.processInstance.details.correlationKey'),
								value: withDash(messageSubscription.correlationKey),
							},
							{
								key: 'message-subscription-state',
								label: t('operate.processInstance.details.subscriptionState'),
								value: withDash(messageSubscription.messageSubscriptionState),
							},
						]
					: []),
			]
		: [];

	const renderElementInstance = () => {
		if (isFetchingElement) {
			return <ElementInstanceSkeleton />;
		}
		return (
			<StructuredList
				label={t('operate.processInstance.details.listLabel')}
				headerSize="sm"
				headerColumns={[
					{cellContent: t('operate.processInstance.details.property'), width: '30%'},
					{cellContent: t('operate.processInstance.details.value'), width: '70%'},
				]}
				rows={rows.map(({key, label, value}) => ({key, columns: [{cellContent: label}, {cellContent: value}]}))}
			/>
		);
	};

	return (
		<div
			role="region"
			aria-label={t('operate.processInstance.details.title')}
			className="flex h-full min-h-0 flex-col gap-4 overflow-y-auto px-4 pb-4 [&>*]:shrink-0"
		>
			{resolvedElementType === 'USER_TASK' && diagram !== undefined && !isCamundaUserTask(businessObject) && (
				<Alert
					variant="warning"
					title={t('operate.processInstance.details.deprecatedUserTask')}
					description={t('operate.processInstance.details.deprecatedUserTaskHint')}
				/>
			)}
			<ProcessInstanceWaitingStatus waitStates={waitStates} />
			<section className="flex flex-col gap-2" aria-busy={isFetchingElement}>
				<Heading as="h2" variant="heading-sm" className="m-0">
					{t('operate.processInstance.details.elementInstance')}
				</Heading>
				{renderElementInstance()}
			</section>
		</div>
	);
};

export {ProcessInstanceDetails};
