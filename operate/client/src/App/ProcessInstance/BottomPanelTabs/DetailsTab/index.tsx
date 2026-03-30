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
import {useJobs} from 'modules/queries/jobs/useJobs';
import {useDecisionInstancesSearch} from 'modules/queries/decisionInstances/useDecisionInstancesSearch';
import {isCamundaUserTask} from 'modules/bpmn-js/utils/isCamundaUserTask';
import {getExecutionDuration} from './getExecutionDuration';
import {EmptyMessageContainer, Container, Callout} from './styled';
import {StructuredList} from 'modules/components/StructuredList';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';

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

  const businessObject = selectedElementId
    ? xmlData?.businessObjects[selectedElementId]
    : null;

  const elementInstanceKey =
    resolvedElementInstance?.elementInstanceKey ?? null;
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

  const rows = useMemo(() => {
    if (!resolvedElementInstance) {
      return [];
    }

    const {startDate, endDate} = resolvedElementInstance;

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
    elementInstanceKey,
    job,
    businessObject,
    calledProcessInstance,
    calledProcessInstancesCount,
    processInstance,
    calledDecisionInstance,
  ]);

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
