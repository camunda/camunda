/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {StructuredListSkeleton} from '@carbon/react';
import {Link} from 'modules/components/Link';
import {Paths, Locations} from 'modules/Routes';
import {EmptyMessage} from 'modules/components/EmptyMessage';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';
import {useProcessInstancesSearch} from 'modules/queries/processInstance/useProcessInstancesSearch';
import {useElementInstance} from 'modules/queries/elementInstances/useElementInstance';
import {useJobs} from 'modules/queries/jobs/useJobs';
import {useDecisionInstancesSearch} from 'modules/queries/decisionInstances/useDecisionInstancesSearch';
import {isCamundaUserTask} from 'modules/bpmn-js/utils/isCamundaUserTask';
import {getExecutionDuration} from './getExecutionDuration';
import {EmptyMessageContainer, Container, Callout} from './styled';
import {StructuredList} from 'modules/components/StructuredList';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useAgentData} from 'modules/contexts/agentData';
import {
  DefaultAgentDetail,
  IterationDetail,
  ToolCallDetail,
} from '../AiAgentTab/AgentDetailPanel';
import {FlatTraceAgentDetail} from '../AiAgentTab/FlatTraceAgentDetail';

const DetailsTab: React.FC = () => {
  const {
    resolvedElementInstance,
    selectedElementId,
    selectedInstancesCount,
    isFetchingElement,
  } = useProcessInstanceElementSelection();

  const {data: processInstance} = useProcessInstance();
  const {data: xmlData} = useProcessInstanceXml({
    processDefinitionKey: processInstance?.processDefinitionKey,
  });

  const {
    isAgentElement,
    primaryAgentElementInstanceKey,
    agentElementInstanceKeys,
    getAgentDataForElement,
    getIterationForElement,
    getToolCallForElement,
    variant,
  } = useAgentData();

  const businessObject = selectedElementId
    ? xmlData?.businessObjects[selectedElementId]
    : null;

  const showAgentContent =
    isAgentElement(selectedElementId) &&
    primaryAgentElementInstanceKey !== null;

  // Inner ad-hoc subprocess activations share the parent's elementId
  // (`AI_Agent`), so clicking the agent on the BPMN diagram resolves to
  // multiple element instances and `resolvedElementInstance` is null. Look up
  // the outer subprocess instance directly so the agent panel can render
  // regardless of which entry point the user took (diagram vs. history).
  const {data: agentSubprocessInstance} = useElementInstance(
    primaryAgentElementInstanceKey ?? '',
    {enabled: showAgentContent && !!primaryAgentElementInstanceKey},
  );

  // In agent mode `resolvedElementInstance` is null when the user clicked the
  // agent on the diagram, so fall back to the outer subprocess instance — this
  // also routes the dependent queries (useJobs etc.) at the agent's instance,
  // which is what surfaces e.g. the agent's "Retries Left" in the details table.
  const elementInstanceKey =
    resolvedElementInstance?.elementInstanceKey ??
    (showAgentContent
      ? (agentSubprocessInstance?.elementInstanceKey ?? null)
      : null);
  const resolvedElementType = resolvedElementInstance?.type;

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

  const agentData = useMemo(() => {
    if (!showAgentContent) {
      return null;
    }
    // Try the user's resolved selection first — when they pick a row in
    // instance-history that IS an agent subprocess, that key is in the map.
    const resolvedKey = resolvedElementInstance?.elementInstanceKey;
    if (resolvedKey) {
      const direct = getAgentDataForElement(resolvedKey);
      if (direct) {
        return direct;
      }
      // Resolved to something specific (tool / inner-instance / nested task)
      // whose key isn't a subprocess key. In single-agent scenarios fall back
      // to primary so tool selections still route to ToolCallDetail. In
      // multi-agent scenarios we can't reliably attribute the selection to
      // one specific run from this key alone, so drop to the standard non-
      // agent rendering (the falsy return below).
      if (agentElementInstanceKeys.length > 1) {
        return null;
      }
    }
    // No resolved selection (diagram click on an ambiguous element) OR
    // single-agent fall-back: use primary.
    if (!primaryAgentElementInstanceKey) {
      return null;
    }
    return getAgentDataForElement(primaryAgentElementInstanceKey);
  }, [
    showAgentContent,
    resolvedElementInstance,
    primaryAgentElementInstanceKey,
    agentElementInstanceKeys,
    getAgentDataForElement,
  ]);

  const rows = useMemo(() => {
    // In agent mode, clicking AI_Agent on the diagram matches every inner
    // instance, so `resolvedElementInstance` is null. Fall back to the outer
    // agent subprocess instance so the details table still populates.
    const source =
      resolvedElementInstance ??
      (showAgentContent ? agentSubprocessInstance : null);
    if (!source) {
      return [];
    }

    const {startDate, endDate} = source;

    const baseRows: Array<{
      key: string;
      columns: Array<{cellContent: React.ReactNode; width?: string}>;
    }> = [
      {
        key: 'element-instance-key',
        columns: [
          {cellContent: 'Element Instance Key'},
          {cellContent: source.elementInstanceKey ?? '-'},
        ],
      },
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

    return baseRows;
  }, [
    resolvedElementInstance,
    showAgentContent,
    agentSubprocessInstance,
    job,
    businessObject,
    calledProcessInstance,
    calledProcessInstancesCount,
    processInstance,
    calledDecisionInstance,
  ]);

  // When an agent element is selected, show the appropriate agent detail view
  // inline inside the Details tab (no dedicated tab). This must happen before
  // the multi-instance empty-state below: clicking AI_Agent on the diagram
  // matches every inner-instance row, so `resolvedElementInstance` is null.
  if (showAgentContent && agentData && selectedElementId && rows.length > 0) {
    const iteration = getIterationForElement(selectedElementId);
    const toolInfo = !iteration
      ? getToolCallForElement(selectedElementId)
      : null;

    return (
      <Container
        data-testid="details-tab"
        style={{flex: 1, minHeight: 0, overflowY: 'auto'}}
      >
        <div style={{flexShrink: 0}}>
          <StructuredList
            label="Element Instance Details"
            headerSize="sm"
            headerColumns={[
              {cellContent: 'Property', width: '30%'},
              {cellContent: 'Value', width: '70%'},
            ]}
            rows={rows}
          />
        </div>
        <h4
          style={{
            flexShrink: 0,
            fontSize: 'var(--cds-heading-compact-01-font-size)',
            fontWeight: 'var(--cds-heading-compact-01-font-weight)',
            lineHeight: 'var(--cds-heading-compact-01-line-height)',
            letterSpacing: 'var(--cds-heading-compact-01-letter-spacing)',
            color: 'var(--cds-text-primary)',
            margin: 0,
          }}
        >
          AI Agent
        </h4>
        {iteration ? (
          <IterationDetail iteration={iteration} />
        ) : toolInfo ? (
          <ToolCallDetail tool={toolInfo.tool} iteration={toolInfo.iteration} />
        ) : variant === 'flat-trace' ? (
          <FlatTraceAgentDetail agentData={agentData} />
        ) : (
          <DefaultAgentDetail agentData={agentData} />
        )}
      </Container>
    );
  }

  // If showAgentContent is true but we can't find agent data (e.g., selected a
  // tool element-instance in the multi-instance scenario whose key isn't in
  // the per-subprocess data map), fall through to the standard non-agent
  // render below — the StructuredList with Element Instance Key / Duration /
  // Retries.

  if (isFetchingElement) {
    return <StructuredListSkeleton rowCount={5} />;
  }

  if (resolvedElementInstance === null) {
    const isMultiInstance =
      selectedInstancesCount !== null && selectedInstancesCount > 1;

    return (
      <EmptyMessageContainer>
        <EmptyMessage
          message={
            isMultiInstance
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
      <StructuredList
        label="Element Instance Details"
        headerSize="sm"
        headerColumns={[
          {cellContent: 'Property', width: '30%'},
          {cellContent: 'Value', width: '70%'},
        ]}
        rows={rows}
      />
    </Container>
  );
};

export {DetailsTab};
