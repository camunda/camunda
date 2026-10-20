/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {StructuredListSkeleton} from '@carbon/react';
import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import type {TFunction} from 'i18next';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/EmptyMessage';
import {StructuredList} from '#/operate/shared/StructuredList/StructuredList';
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
} from './details.queries';
import {
	Callout,
	Container,
	ElementInstanceHint,
	EmptyMessageContainer,
	SectionContainer,
	SectionHeading,
} from './details.styled';
import {getExecutionDuration} from './getExecutionDuration';
import {ProcessInstanceWaitingStatus} from './ProcessInstanceWaitingStatus';
import {ProcessInstanceLink} from './styled';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';
import {useProcessInstancePage} from './useProcessInstancePage';

type Row = {key: string; label: string; value: React.ReactNode};

function getMultipleInstancesMessage(t: TFunction, hasMultipleInstances: boolean, fallbackKey: string) {
	return hasMultipleInstances ? t('operate.processInstance.details.multiInstance') : t(fallbackKey);
}

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
	} = useProcessInstanceElementSelection();
	const {data: diagram} = useDiagramXml(processInstance.processDefinitionKey);

	const isProcessScope = !hasSelection;
	const isRunning = isInstanceRunning(processInstance);
	const businessObject = diagram?.businessObjects[anchorElementId ?? selectedElementId ?? ''];
	const elementInstanceKey = resolvedElementInstance?.elementInstanceKey;
	const resolvedElementType = resolvedElementInstance?.type;

	const waitStatesKey = isProcessScope ? processInstanceId : elementInstanceKey;
	const isWaitStatesEnabled =
		waitStatesKey !== undefined && isRunning && (isProcessScope || resolvedElementInstance?.state === 'ACTIVE');
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
			<Container role="region" aria-label={t('operate.processInstance.details.title')}>
				<ProcessInstanceWaitingStatus waitStates={waitStates} />
			</Container>
		);
	}

	const hasMultipleInstances = selectedElementInstanceCount !== null && selectedElementInstanceCount > 1;

	if (resolvedElementInstance === null && !isFetchingElement) {
		return (
			<Container $isEmpty role="region" aria-label={t('operate.processInstance.details.title')}>
				<EmptyMessageContainer>
					<EmptyMessage
						message={getMultipleInstancesMessage(t, hasMultipleInstances, 'operate.processInstance.details.noElement')}
					/>
				</EmptyMessageContainer>
			</Container>
		);
	}

	const calledProcessInstancesCount = calledProcessInstances?.page.totalItems ?? 0;
	const calledProcessInstance = calledProcessInstancesCount === 1 ? calledProcessInstances?.items[0] : undefined;
	const tasklistFilter = userTask?.state === 'COMPLETED' ? 'completed' : 'all-open';
	const calledProcessName = calledProcessInstance?.processDefinitionName ?? calledProcessInstance?.processDefinitionId;
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
									<ProcessInstanceLink
										to="/tasklist/$userTaskKey"
										params={{userTaskKey: userTask.userTaskKey}}
										search={{filter: tasklistFilter}}
										target="_blank"
										rel="noopener noreferrer"
									>
										{t('operate.processInstance.details.openTasklist')}
									</ProcessInstanceLink>
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
								value:
									calledProcessInstancesCount > 1 ? (
										<ProcessInstanceLink
											to="/operate/processes"
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
										>
											{t('operate.processInstance.details.viewAllCalledCount', {count: calledProcessInstancesCount})}
										</ProcessInstanceLink>
									) : calledProcessInstance ? (
										<ProcessInstanceLink
											to="/operate/processes/$processInstanceId"
											params={{processInstanceId: calledProcessInstance.processInstanceKey}}
											title={t('operate.processInstance.details.viewInstance', {
												name: calledProcessName,
												key: calledProcessInstance.processInstanceKey,
											})}
											aria-label={t('operate.processInstance.details.viewInstance', {
												name: calledProcessName,
												key: calledProcessInstance.processInstanceKey,
											})}
										>
											{`${calledProcessName} - ${calledProcessInstance.processInstanceKey}`}
										</ProcessInstanceLink>
									) : calledProcessInstancesCount === 0 ? (
										t('operate.processInstance.details.none')
									) : null,
							},
						]
					: []),
				...(isBusinessRuleTask
					? [
							{
								key: 'called-decision-instance',
								label: t('operate.processInstance.details.calledDecisionInstance'),
								value: calledDecisionInstance ? (
									<ProcessInstanceLink
										to="/operate/decisions/$decisionInstanceId"
										params={{decisionInstanceId: calledDecisionInstance.decisionEvaluationInstanceKey}}
										title={t('operate.processInstance.details.viewInstance', {
											name: calledDecisionInstance.decisionDefinitionName,
											key: calledDecisionInstance.decisionEvaluationInstanceKey,
										})}
										aria-label={t('operate.processInstance.details.viewInstance', {
											name: calledDecisionInstance.decisionDefinitionName,
											key: calledDecisionInstance.decisionEvaluationInstanceKey,
										})}
									>
										{`${calledDecisionInstance.decisionDefinitionName} - ${calledDecisionInstance.decisionEvaluationInstanceKey}`}
									</ProcessInstanceLink>
								) : (
									'-'
								),
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

	return (
		<Container role="region" aria-label={t('operate.processInstance.details.title')}>
			{resolvedElementType === 'USER_TASK' && !isCamundaUserTask(businessObject) && (
				<Callout
					kind="warning"
					lowContrast
					title={t('operate.processInstance.details.deprecatedUserTask')}
					subtitle={t('operate.processInstance.details.deprecatedUserTaskHint')}
				/>
			)}
			<ProcessInstanceWaitingStatus waitStates={waitStates} />
			<SectionContainer>
				<SectionHeading>{t('operate.processInstance.details.elementInstance')}</SectionHeading>
				{isFetchingElement ? (
					<StructuredListSkeleton rowCount={5} />
				) : resolvedElementInstance === null ? (
					<ElementInstanceHint>
						{getMultipleInstancesMessage(t, hasMultipleInstances, 'operate.processInstance.details.noElementInstance')}
					</ElementInstanceHint>
				) : (
					<StructuredList
						label={t('operate.processInstance.details.listLabel')}
						headerSize="sm"
						headerColumns={[
							{cellContent: t('operate.processInstance.details.property'), width: '30%'},
							{cellContent: t('operate.processInstance.details.value'), width: '70%'},
						]}
						rows={rows.map(({key, label, value}) => ({key, columns: [{cellContent: label}, {cellContent: value}]}))}
					/>
				)}
			</SectionContainer>
		</Container>
	);
};

export {ProcessInstanceDetails};
