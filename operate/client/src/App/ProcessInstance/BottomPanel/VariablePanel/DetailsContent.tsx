/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useMemo} from 'react';
import {Link} from 'modules/components/Link';
import {Paths} from 'modules/Routes';
import {tracking} from 'modules/tracking';
import {Launch} from '@carbon/react/icons';
import {
  Content,
  StructuredList,
  EmptyMessageWrapper,
  FooterContainer,
  FooterLayer,
} from './DetailsContent.styled';
import {Button} from '@carbon/react';
import {getExecutionDuration} from 'App/ProcessInstance/TopPanel/MetadataPopover/Details/getExecutionDuration';
import type {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {DetailsModal} from 'App/ProcessInstance/TopPanel/MetadataPopover/Details/DetailsModal';
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {useProcessInstancesSearch} from 'modules/queries/processInstance/useProcessInstancesSearch';
import {useJobs} from 'modules/queries/jobs/useJobs';
import {useDecisionInstancesSearch} from 'modules/queries/decisionInstances/useDecisionInstancesSearch';
import {useElementInstance} from 'modules/queries/elementInstances/useElementInstance';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';
import {convertBpmnJsTypeToAPIType} from 'App/ProcessInstance/TopPanel/MetadataPopover/convertBpmnJsTypeToAPIType';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useElementInstancesSearch} from 'modules/queries/elementInstances/useElementInstancesSearch';
import {observer} from 'mobx-react';
import {EmptyMessage} from 'modules/components/EmptyMessage';

const DetailsContent: React.FC = observer(() => {
  const [isModalVisible, setIsModalVisible] = useState(false);
  const {data: processInstance} = useProcessInstance();
  const selection = flowNodeSelectionStore.state.selection;
  const elementId = selection?.flowNodeId;
  const elementInstanceKey = selection?.flowNodeInstanceId;

  const processDefinitionKey = useProcessDefinitionKeyContext();
  const {data} = useProcessInstanceXml({
    processDefinitionKey,
  });
  const businessObject = elementId ? data?.businessObjects[elementId] : null;

  const {data: elementInstance, isLoading: isFetchingInstance} =
    useElementInstance(elementInstanceKey ?? '', {
      enabled: !!elementInstanceKey && !!elementId,
    });

  const {
    data: elementInstancesSearchResult,
    isLoading: isSearchingElementInstances,
  } = useElementInstancesSearch({
    elementId: elementId ?? '',
    processInstanceKey: processInstance?.processInstanceKey ?? '',
    elementType: convertBpmnJsTypeToAPIType(businessObject?.$type),
    enabled: !elementInstanceKey && !!elementId && !!processInstance?.processInstanceKey,
  });

  const elementInstanceMetadata = useMemo(() => {
    if (elementInstanceKey && elementInstance) {
      return elementInstance;
    }

    if (
      !elementInstanceKey &&
      elementInstancesSearchResult?.items?.length === 1
    ) {
      return elementInstancesSearchResult.items[0];
    }

    return null;
  }, [
    elementInstanceKey,
    elementInstance,
    elementInstancesSearchResult,
  ]);

  const instanceKey = elementInstanceMetadata?.elementInstanceKey ?? null;
  const type = elementInstanceMetadata?.type;
  const startDate = elementInstanceMetadata?.startDate;
  const endDate = elementInstanceMetadata?.endDate;

  const {data: calledProcessInstancesSearchResult} = useProcessInstancesSearch(
    {
      filter: {
        parentElementInstanceKey: instanceKey ?? '',
      },
    },
    {
      enabled: !!instanceKey && type === 'CALL_ACTIVITY',
    },
  );

  const {data: jobSearchResult} = useJobs({
    payload: {
      filter: {
        elementInstanceKey: instanceKey ?? '',
        listenerEventType: 'UNSPECIFIED',
      },
    },
    disabled: !instanceKey,
    select: (data) => data.pages?.flatMap((page) => page.items),
  });

  const {data: decisionInstanceSearchResult} = useDecisionInstancesSearch(
    {
      filter: {
        elementInstanceKey: instanceKey ?? '',
      },
    },
    {
      enabled: !!instanceKey && type === 'BUSINESS_RULE_TASK',
    },
  );

  const calledDecisionInstance = useMemo(
    () =>
      decisionInstanceSearchResult?.items?.find(
        (instance) =>
          instance.rootDecisionDefinitionKey === instance.decisionDefinitionKey,
      ),
    [decisionInstanceSearchResult],
  );

  const calledProcessInstance = calledProcessInstancesSearchResult?.items?.[0];
  const job = jobSearchResult?.[0];

  const rows = useMemo(() => {
    if (!elementInstanceMetadata) {
      return [];
    }
    const baseRows: Array<{
      key: string;
      columns: Array<{cellContent: React.ReactNode; width?: string}>;
    }> = [
      {
        key: 'element-instance-key',
        columns: [
          {cellContent: 'Element Instance Key'},
          {cellContent: instanceKey ?? '—'},
        ],
      },
      {
        key: 'execution-duration',
        columns: [
          {cellContent: 'Execution Duration'},
          {
            cellContent: startDate
              ? getExecutionDuration(startDate, endDate)
              : '—',
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
      type !== 'MULTI_INSTANCE_BODY'
    ) {
      baseRows.push({
        key: 'called-process-instance',
        columns: [
          {cellContent: 'Called Process Instance'},
          {
            cellContent: calledProcessInstance ? (
              <Link
                to={Paths.processInstance(
                  calledProcessInstance.processInstanceKey,
                )}
                title={`View ${calledProcessInstance.processDefinitionName} instance ${calledProcessInstance.processInstanceKey}`}
                aria-label={`View ${calledProcessInstance.processDefinitionName} instance ${calledProcessInstance.processInstanceKey}`}
                data-testid="called-process-instance"
              >
                {`${calledProcessInstance.processDefinitionName} - ${calledProcessInstance.processInstanceKey}`}
              </Link>
            ) : (
              'None'
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
              '—'
            ),
          },
        ],
      });
    }

    return baseRows;
  }, [
    instanceKey,
    startDate,
    endDate,
    job,
    businessObject,
    type,
    calledProcessInstance,
    calledDecisionInstance,
    elementInstanceMetadata,
  ]);

  if (
    elementId === undefined ||
    isFetchingInstance ||
    isSearchingElementInstances ||
    !elementInstanceMetadata
  ) {
    return (
      <Content>
        <EmptyMessageWrapper>
          <EmptyMessage message="No additional details available" />
        </EmptyMessageWrapper>
      </Content>
    );
  }

  return (
    <>
      <Content>
        <StructuredList
          label="Element Instance Details"
          headerSize="sm"
          headerColumns={[
            {cellContent: 'Property', width: '40%'},
            {cellContent: 'Value', width: '60%'},
          ]}
          verticalCellPadding="var(--cds-spacing-02)"
          rows={rows}
        />
        {instanceKey !== null && (
          <FooterContainer>
            <FooterLayer>
              <Button
                kind="ghost"
                size="sm"
                onClick={() => {
                  setIsModalVisible(true);
                  tracking.track({
                    eventName: 'flow-node-instance-details-opened',
                  });
                }}
                title="Show more metadata"
                aria-label="Show more metadata"
                renderIcon={Launch}
                style={{alignSelf: 'flex-start'}}
              >
                View metadata
              </Button>
            </FooterLayer>
          </FooterContainer>
        )}
      </Content>
      {instanceKey !== null && (
        <DetailsModal
          elementInstance={elementInstanceMetadata}
          job={job}
          calledProcessInstance={calledProcessInstance}
          calledDecisionInstance={calledDecisionInstance}
          isVisible={isModalVisible}
          onClose={() => setIsModalVisible(false)}
        />
      )}
    </>
  );
});

export {DetailsContent};
