/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {StructuredListSkeleton} from '@carbon/react';
import isNil from 'lodash/isNil';
import {Link} from 'modules/components/Link';
import {Paths, Locations} from 'modules/Routes';
import {tracking} from 'modules/tracking';
import {EmptyMessage} from 'modules/components/EmptyMessage';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';
import {useProcessInstancesSearch} from 'modules/queries/processInstance/useProcessInstancesSearch';
import {useJobs} from 'modules/queries/jobs/useJobs';
import {useDecisionInstancesSearch} from 'modules/queries/decisionInstances/useDecisionInstancesSearch';
import {isCamundaUserTask} from 'modules/bpmn-js/utils/isCamundaUserTask';
import {isMessageEventElement} from 'modules/bpmn-js/utils/isMessageEventElement';
import {useSearchUserTasks} from 'modules/queries/userTasks/useSearchUserTasks';
import {useMessageSubscriptions} from 'modules/queries/messageSubscriptions/useMessageSubscriptions';
import {getClientConfig} from 'modules/utils/getClientConfig';
import {mergePathname} from 'modules/request/mergePathname';
import {getExecutionDuration} from './getExecutionDuration';
import {
  EmptyMessageContainer,
  Container,
  Callout,
  SectionContainer,
  SectionHeading,
  ElementInstanceHint,
} from './styled';
import {StructuredList} from 'modules/components/StructuredList';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useAgentInstancesForElement} from 'modules/queries/agentInstances/useAgentInstancesForElement';
import {AgentDetails} from './AgentDetails';
import {useProcessInstancePageParams} from '../../useProcessInstancePageParams';
import {useElementInstanceInspection} from 'modules/queries/elementInstanceInspection/useElementInstanceInspection';
import {WaitingStatus} from './WaitingStatus';
import type {UserTask} from '@camunda/camunda-api-zod-schemas/8.10';

const formatTaskLink = (
  tasklistUrl: string,
  userTaskKey: string,
  state: UserTask['state'],
) => {
  const url = new URL(tasklistUrl);
  const filter = state === 'COMPLETED' ? 'completed' : 'all-open';
  url.pathname = mergePathname(url.pathname, userTaskKey);
  url.searchParams.set('filter', filter);
  return url.toString();
};

const DetailsTab: React.FC = () => {
  const clientConfig = getClientConfig();
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const {
    hasSelection,
    resolvedElementInstance,
    selectedElementId,
    selectedAnchorElementId,
    selectedInstancesCount,
    isSelectedInstanceMultiInstanceBody,
    isFetchingElement,
  } = useProcessInstanceElementSelection();

  // No element selected means the process scope (the PROCESS root row).
  const isProcessScope = !hasSelection;

  const {data: processInstance} = useProcessInstance();
  const {data: xmlData} = useProcessInstanceXml({
    processDefinitionKey: processInstance?.processDefinitionKey,
  });

  const effectiveElementId = selectedAnchorElementId ?? selectedElementId;
  const businessObject = effectiveElementId
    ? xmlData?.businessObjects[effectiveElementId]
    : null;

  const elementInstanceKey =
    resolvedElementInstance?.elementInstanceKey ?? null;
  const resolvedElementType = resolvedElementInstance?.type;

  // For the process scope the wait state is anchored on the PROCESS container,
  // whose element instance key equals the process instance key.
  const inspectionElementInstanceKey = isProcessScope
    ? processInstanceId
    : (elementInstanceKey ?? undefined);

  const {data: inspectionData} = useElementInstanceInspection({
    processInstanceKey: processInstanceId,
    elementInstanceKey: inspectionElementInstanceKey,
    enabled:
      clientConfig.waitStatesEnabled &&
      !!inspectionElementInstanceKey &&
      processInstance?.state === 'ACTIVE' &&
      (isProcessScope || resolvedElementInstance?.state === 'ACTIVE'),
  });

  const waitStates = useMemo(() => {
    return (
      inspectionData?.items?.filter(
        (item) => item.elementInstanceKey === inspectionElementInstanceKey,
      ) ?? []
    );
  }, [inspectionData, inspectionElementInstanceKey]);

  const {data: calledProcessInstancesSearchResult} = useProcessInstancesSearch(
    {
      filter: {
        parentElementInstanceKey: elementInstanceKey ?? '',
      },
    },
    {
      enabled: !!elementInstanceKey && resolvedElementType === 'CALL_ACTIVITY',
    },
  );

  const {data: jobSearchResult} = useJobs({
    payload: {
      filter: {
        elementInstanceKey: elementInstanceKey ?? '',
        listenerEventType: 'UNSPECIFIED',
      },
    },
    enabled: !!elementInstanceKey,
    select: (data) => data.pages?.flatMap((page) => page.items),
  });

  const {data: decisionInstanceSearchResult} = useDecisionInstancesSearch(
    {
      filter: {
        elementInstanceKey: elementInstanceKey ?? '',
      },
    },
    {
      enabled:
        !!elementInstanceKey && resolvedElementType === 'BUSINESS_RULE_TASK',
    },
  );

  const isCamundaTask =
    resolvedElementType === 'USER_TASK' && isCamundaUserTask(businessObject);

  const {data: userTaskSearchResult} = useSearchUserTasks(
    {
      filter: {elementInstanceKey: elementInstanceKey ?? ''},
      page: {limit: 1},
    },
    {
      enabled: !!elementInstanceKey && isCamundaTask,
    },
  );

  const userTask = userTaskSearchResult?.items?.[0] ?? null;

  const isMessageElement = isMessageEventElement(businessObject);

  const {data: messageSubscriptionResult} = useMessageSubscriptions(
    {
      filter: {elementInstanceKey: {$eq: elementInstanceKey ?? ''}},
      page: {limit: 1},
    },
    {
      enabled: !!elementInstanceKey && isMessageElement,
    },
  );

  const messageSubscription = messageSubscriptionResult?.items?.[0] ?? null;

  // Multi-instance bodies have no agent instance associated with the multi-instance
  // element instance. Agents are assigned to the spawned inner instances.
  // Fall back to elementId-only filtering when multi-instance body is selected.
  const agentElementInstanceKey = isSelectedInstanceMultiInstanceBody
    ? null
    : elementInstanceKey;

  const {
    data: agentInstancesResult,
    isLoading: isAgentLoading,
    isError: isAgentError,
  } = useAgentInstancesForElement({
    processInstanceKey: processInstance?.processInstanceKey ?? '',
    elementId: effectiveElementId ?? '',
    elementInstanceKey: agentElementInstanceKey,
    enabled: !!processInstance?.processInstanceKey && !!effectiveElementId,
    enablePeriodicRefetch: true,
  });
  const showAgentInstance =
    (agentInstancesResult && agentInstancesResult.items.length > 0) ||
    isAgentLoading ||
    isAgentError;

  const calledDecisionInstance = decisionInstanceSearchResult?.items?.find(
    (instance) =>
      instance.rootDecisionDefinitionKey === instance.decisionDefinitionKey,
  );

  const calledProcessInstancesCount =
    calledProcessInstancesSearchResult?.page.totalItems ?? 0;
  const calledProcessInstance =
    calledProcessInstancesCount === 1
      ? calledProcessInstancesSearchResult?.items?.[0]
      : undefined;
  const job = jobSearchResult?.[0];

  const rows = useMemo(() => {
    if (!resolvedElementInstance) {
      return [];
    }

    const {startDate, endDate} = resolvedElementInstance;

    const taskLink =
      !isNil(clientConfig.tasklistUrl) && isCamundaTask && userTask?.userTaskKey
        ? formatTaskLink(
            clientConfig.tasklistUrl,
            userTask.userTaskKey,
            userTask.state,
          )
        : null;

    const baseRows: Array<{
      key: string;
      columns: Array<{cellContent: React.ReactNode; width?: string}>;
    }> = [
      {
        key: 'element-instance-key',
        columns: [
          {cellContent: 'Element Instance Key'},
          {cellContent: elementInstanceKey ?? '-'},
        ],
      },
      ...(taskLink !== null
        ? [
            {
              key: 'open-tasklist',
              columns: [
                {cellContent: 'Tasklist'},
                {
                  cellContent: (
                    <a
                      href={taskLink}
                      target="_blank"
                      rel="noopener noreferrer"
                      onClick={() => {
                        tracking.track({
                          eventName: 'open-tasklist-link-clicked',
                        });
                      }}
                    >
                      Open Tasklist
                    </a>
                  ),
                },
              ],
            },
          ]
        : []),
      {
        key: 'execution-duration',
        columns: [
          {cellContent: 'Execution Duration'},
          {
            cellContent: startDate
              ? getExecutionDuration(startDate, endDate)
              : '-',
          },
        ],
      },
    ];

    if (job?.retries !== undefined) {
      baseRows.push({
        key: 'retries-left',
        columns: [
          {cellContent: 'Retries Left'},
          {
            cellContent: (
              <span data-testid="retries-left-count">{job.retries}</span>
            ),
          },
        ],
      });
    }

    if (job?.priority !== undefined) {
      baseRows.push({
        key: 'job-priority',
        columns: [
          {cellContent: 'Job Priority'},
          {
            cellContent: <span data-testid="job-priority">{job.priority}</span>,
          },
        ],
      });
    }

    if (job?.type !== undefined) {
      baseRows.push({
        key: 'job-type',
        columns: [{cellContent: 'Job Type'}, {cellContent: job.type}],
      });
    }

    if (job?.worker !== undefined) {
      baseRows.push({
        key: 'worker',
        columns: [{cellContent: 'Worker'}, {cellContent: job.worker}],
      });
    }

    if (
      businessObject?.$type === 'bpmn:CallActivity' &&
      calledProcessInstancesCount === 0
    ) {
      baseRows.push({
        key: 'called-process-instance',
        columns: [
          {cellContent: 'Called Process Instance'},
          {
            cellContent: 'None',
          },
        ],
      });
    }

    if (
      businessObject?.$type === 'bpmn:CallActivity' &&
      calledProcessInstancesCount > 1
    ) {
      baseRows.push({
        key: 'called-process-instance',
        columns: [
          {cellContent: 'Called Process Instance'},
          {
            cellContent: (
              <Link
                to={Locations.processes({
                  parentProcessInstanceKey: processInstance!.processInstanceKey,
                  active: true,
                  incidents: true,
                  completed: true,
                  canceled: true,
                })}
                title={`View all called process instances`}
                aria-label={`View all called process instances`}
              >
                {`View all (${calledProcessInstancesCount})`}
              </Link>
            ),
          },
        ],
      });
    }

    if (
      businessObject?.$type === 'bpmn:CallActivity' &&
      calledProcessInstance !== undefined &&
      calledProcessInstancesCount === 1
    ) {
      baseRows.push({
        key: 'called-process-instance',
        columns: [
          {cellContent: 'Called Process Instance'},
          {
            cellContent: (
              <Link
                to={Paths.processInstance(
                  calledProcessInstance.processInstanceKey,
                )}
                title={`View ${calledProcessInstance.processDefinitionName} instance ${calledProcessInstance.processInstanceKey}`}
                aria-label={`View ${calledProcessInstance.processDefinitionName} instance ${calledProcessInstance.processInstanceKey}`}
              >
                {`${calledProcessInstance.processDefinitionName} - ${calledProcessInstance.processInstanceKey}`}
              </Link>
            ),
          },
        ],
      });
    }

    if (businessObject?.$type === 'bpmn:BusinessRuleTask') {
      baseRows.push({
        key: 'called-decision-instance',
        columns: [
          {cellContent: 'Called Decision Instance'},
          {
            cellContent: calledDecisionInstance ? (
              <Link
                to={Paths.decisionInstance(
                  calledDecisionInstance.decisionEvaluationInstanceKey,
                )}
                title={`View ${calledDecisionInstance.decisionDefinitionName} instance ${calledDecisionInstance.decisionEvaluationInstanceKey}`}
                aria-label={`View ${calledDecisionInstance.decisionDefinitionName} instance ${calledDecisionInstance.decisionEvaluationInstanceKey}`}
              >
                {`${calledDecisionInstance.decisionDefinitionName} - ${calledDecisionInstance.decisionEvaluationInstanceKey}`}
              </Link>
            ) : (
              '-'
            ),
          },
        ],
      });
    }

    if (isCamundaTask && userTask !== null) {
      baseRows.push(
        {
          key: 'assignee',
          columns: [
            {cellContent: 'Assignee'},
            {cellContent: userTask.assignee ?? '-'},
          ],
        },
        {
          key: 'candidate-users',
          columns: [
            {cellContent: 'Candidate Users'},
            {
              cellContent:
                userTask.candidateUsers.length > 0
                  ? userTask.candidateUsers.join(', ')
                  : '-',
            },
          ],
        },
        {
          key: 'candidate-groups',
          columns: [
            {cellContent: 'Candidate Groups'},
            {
              cellContent:
                userTask.candidateGroups.length > 0
                  ? userTask.candidateGroups.join(', ')
                  : '-',
            },
          ],
        },
        {
          key: 'due-date',
          columns: [
            {cellContent: 'Due Date'},
            {cellContent: userTask.dueDate ?? '-'},
          ],
        },
        {
          key: 'follow-up-date',
          columns: [
            {cellContent: 'Follow-up Date'},
            {cellContent: userTask.followUpDate ?? '-'},
          ],
        },
        {
          key: 'priority',
          columns: [
            {cellContent: 'Priority'},
            {cellContent: String(userTask.priority)},
          ],
        },
        {
          key: 'form-key',
          columns: [
            {cellContent: 'Form Key'},
            {cellContent: userTask.formKey ?? '-'},
          ],
        },
      );
    }

    if (isMessageElement && messageSubscription !== null) {
      baseRows.push(
        {
          key: 'message-name',
          columns: [
            {cellContent: 'Message Name'},
            {cellContent: messageSubscription.messageName ?? '-'},
          ],
        },
        {
          key: 'correlation-key',
          columns: [
            {cellContent: 'Correlation Key'},
            {cellContent: messageSubscription.correlationKey ?? '-'},
          ],
        },
        {
          key: 'message-subscription-state',
          columns: [
            {cellContent: 'Subscription State'},
            {cellContent: messageSubscription.messageSubscriptionState ?? '-'},
          ],
        },
      );
    }

    return baseRows;
  }, [
    resolvedElementInstance,
    elementInstanceKey,
    job,
    businessObject,
    calledProcessInstance,
    calledProcessInstancesCount,
    processInstance,
    calledDecisionInstance,
    clientConfig.tasklistUrl,
    isCamundaTask,
    userTask,
    isMessageElement,
    messageSubscription,
  ]);

  if (isProcessScope) {
    // The process scope only surfaces the process-level wait state status
    // (the Details tab is only shown when such a wait state exists).
    return (
      <Container data-testid="details-tab">
        <WaitingStatus waitStates={waitStates} />
      </Container>
    );
  }

  if (isFetchingElement) {
    return <StructuredListSkeleton rowCount={5} />;
  }

  const hasMultipleInstances =
    selectedInstancesCount !== null && selectedInstancesCount > 1;

  if (resolvedElementInstance === null && !showAgentInstance) {
    return (
      <EmptyMessageContainer>
        <EmptyMessage
          message={
            hasMultipleInstances
              ? 'To view the details, select a single element instance in the instance history.'
              : 'There is no element selected.'
          }
        />
      </EmptyMessageContainer>
    );
  }

  return (
    <Container data-testid="details-tab">
      {resolvedElementType === 'USER_TASK' &&
        !isCamundaUserTask(businessObject) && (
          <Callout
            kind="warning"
            lowContrast
            title="User tasks with job worker implementation are deprecated."
            subtitle="Consider migrating to Camunda user tasks."
          />
        )}
      {showAgentInstance && (
        <AgentDetails
          agentInstances={agentInstancesResult?.items ?? []}
          totalAgentsCount={agentInstancesResult?.page.totalItems ?? 0}
          hasMoreTotalItems={
            agentInstancesResult?.page.hasMoreTotalItems ?? false
          }
          isLoading={isAgentLoading}
          isError={isAgentError}
          selectedElementInstanceKey={agentElementInstanceKey}
        />
      )}
      {!showAgentInstance && clientConfig.waitStatesEnabled && (
        <WaitingStatus waitStates={waitStates} />
      )}
      <SectionContainer>
        <SectionHeading>Element Instance</SectionHeading>
        {resolvedElementInstance === null ? (
          <ElementInstanceHint>
            {hasMultipleInstances
              ? 'To view the details, select a single element instance in the instance history.'
              : 'There is no element instance selected.'}
          </ElementInstanceHint>
        ) : (
          <StructuredList
            label="Element Instance Details"
            headerSize="sm"
            headerColumns={[
              {cellContent: 'Property', width: '30%'},
              {cellContent: 'Value', width: '70%'},
            ]}
            rows={rows}
          />
        )}
      </SectionContainer>
    </Container>
  );
};

export {DetailsTab};
