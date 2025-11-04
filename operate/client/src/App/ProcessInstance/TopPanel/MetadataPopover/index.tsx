/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Popover, Content, Divider} from './styled';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {observer} from 'mobx-react';
import {flip, offset} from '@floating-ui/react-dom';
import {Header} from './Header';
import {Stack} from '@carbon/react';
import {MultiIncidents} from './Incidents/multiIncidents';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useElementInstancesSearch} from 'modules/queries/elementInstances/useElementInstancesSearch';
import {useElementInstance} from 'modules/queries/elementInstances/useElementInstance';
import {useFlownodeInstancesStatistics} from 'modules/queries/flownodeInstancesStatistics/useFlownodeInstancesStatistics';
import {useMemo} from 'react';
import {Details} from './Details';
import {useGetUserTaskByElementInstance} from 'modules/queries/userTasks/useGetUserTaskByElementInstance';
import {useProcessInstancesSearch} from 'modules/queries/processInstance/useProcessInstancesSearch';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';
import {convertBpmnJsTypeToAPIType} from './convertBpmnJsTypeToAPIType';
import {useJobs} from 'modules/queries/jobs/useJobs';
import {useSearchMessageSubscriptions} from 'modules/queries/messageSubscriptions/useSearchMessageSubscriptions';
import {useDecisionInstancesSearch} from 'modules/queries/decisionInstances/useDecisionInstancesSearch';
import {useDecisionDefinition} from 'modules/queries/decisionDefinitions/useDecisionDefinition';
import {incidentsPanelStore} from 'modules/stores/incidentsPanel';
import {IS_INCIDENTS_PANEL_V2} from 'modules/feature-flags';
import {Incidents} from './Incidents';
import {incidentsStore} from 'modules/stores/incidents';

type Props = {
  selectedFlowNodeRef?: SVGGraphicsElement | null;
};

const MetadataPopover = observer(({selectedFlowNodeRef}: Props) => {
  const {data: processInstance} = useProcessInstance();
  const selection = flowNodeSelectionStore.state.selection;
  const elementId = selection?.flowNodeId;
  const elementInstanceKey = selection?.flowNodeInstanceId;
  const isMultiInstance = selection?.isMultiInstance ?? false;

  const processDefinitionKey = useProcessDefinitionKeyContext();
  const {data} = useProcessInstanceXml({
    processDefinitionKey,
  });
  const businessObject = elementId ? data?.businessObjects[elementId] : null;

  const {data: statistics} = useFlownodeInstancesStatistics();

  const instanceCount = useMemo(() => {
    if (!statistics?.items || !elementId) {
      return null;
    }
    const elementStats = statistics.items.find(
      (stat) => stat.elementId === elementId,
    );
    if (!elementStats) {
      return 0;
    }
    if (isMultiInstance) {
      return 1;
    }
    return (
      elementStats.active +
      elementStats.completed +
      elementStats.canceled +
      elementStats.incidents
    );
  }, [statistics, elementId, isMultiInstance]);

  const incidentCount =
    statistics?.items.find((stat) => stat.elementId === elementId)?.incidents ??
    0;

  const shouldFetchElementInstances =
    instanceCount === 1 &&
    !elementInstanceKey &&
    !!processInstance?.processInstanceKey &&
    !!elementId;

  const {data: elementInstance, isLoading: isFetchingInstance} =
    useElementInstance(elementInstanceKey ?? '', {
      enabled: !!elementInstanceKey && !!elementId,
    });

  const {
    data: elementInstancesSearchResult,
    isLoading: isSearchingElementInstances,
  } = useElementInstancesSearch(
    elementId ?? '',
    processInstance?.processInstanceKey ?? '',
    convertBpmnJsTypeToAPIType(businessObject?.$type),
    {
      enabled: shouldFetchElementInstances,
    },
  );

  const elementInstanceMetadata = useMemo(() => {
    if (elementInstanceKey && elementInstance) {
      return elementInstance;
    }

    if (
      !elementInstanceKey &&
      instanceCount === 1 &&
      elementInstancesSearchResult?.items?.length === 1
    ) {
      return elementInstancesSearchResult.items[0];
    }

    return null;
  }, [
    elementInstanceKey,
    elementInstance,
    instanceCount,
    elementInstancesSearchResult,
  ]);

  const {data: userTask, isLoading: isSearchingUserTasks} =
    useGetUserTaskByElementInstance(
      elementInstanceMetadata?.elementInstanceKey ?? '',
      {
        enabled:
          !!elementInstanceMetadata?.elementInstanceKey &&
          elementInstanceMetadata?.type === 'USER_TASK',
      },
    );

  const {
    data: processInstancesSearchResult,
    isLoading: isSearchingProcessInstances,
  } = useProcessInstancesSearch(
    {
      filter: {
        parentElementInstanceKey:
          elementInstanceMetadata?.elementInstanceKey ?? '',
      },
    },
    {
      enabled: !!elementInstanceMetadata?.elementInstanceKey,
    },
  );

  const {data: jobSearchResult, isLoading: isSearchingJob} = useJobs({
    payload: {
      filter: {
        elementInstanceKey: elementInstanceMetadata?.elementInstanceKey ?? '',
        listenerEventType: 'UNSPECIFIED',
      },
    },
    disabled: !elementInstanceMetadata?.elementInstanceKey,
    select: (data) => data.pages?.flatMap((page) => page.items),
  });

  const {
    data: messageSubscriptionSearchResult,
    isLoading: isSearchingMessageSubscription,
  } = useSearchMessageSubscriptions(
    {
      filter: {
        elementInstanceKey: elementInstanceMetadata?.elementInstanceKey ?? '',
      },
    },
    {
      enabled: !!elementInstanceMetadata?.elementInstanceKey,
    },
  );

  const {
    data: decisionInstanceSearchResult,
    isLoading: isSearchingDecisionInstances,
  } = useDecisionInstancesSearch(
    {
      filter: {
        elementInstanceKey: elementInstanceMetadata?.elementInstanceKey ?? '',
      },
    },
    {
      enabled:
        !!elementInstanceMetadata?.elementInstanceKey &&
        elementInstanceMetadata?.type === 'BUSINESS_RULE_TASK',
    },
  );

  const calledDecisionInstance = decisionInstanceSearchResult?.items?.find(
    (instance) =>
      instance.rootDecisionDefinitionKey === instance.decisionDefinitionKey,
  );

  const calledDecisionDefinitionId =
    decisionInstanceSearchResult?.items?.[0]?.rootDecisionDefinitionKey;

  const {
    data: calledDecisionDefinition,
    isLoading: isSearchingCalledDecisionDefinition,
  } = useDecisionDefinition(calledDecisionDefinitionId ?? '', {
    enabled: !!calledDecisionDefinitionId && !calledDecisionInstance,
  });

  if (
    elementId === undefined ||
    (shouldFetchElementInstances && isSearchingElementInstances) ||
    (!!elementInstanceKey && isFetchingInstance) ||
    isSearchingUserTasks ||
    isSearchingProcessInstances ||
    isSearchingJob ||
    isSearchingMessageSubscription ||
    isSearchingCalledDecisionDefinition ||
    isSearchingDecisionInstances
  ) {
    return null;
  }

  return (
    <Popover
      referenceElement={selectedFlowNodeRef}
      middlewareOptions={[
        offset(10),
        flip({
          fallbackPlacements: ['top', 'right', 'left'],
        }),
      ]}
      variant="arrow"
    >
      <Stack gap={3}>
        {instanceCount !== null && instanceCount > 1 && !elementInstanceKey && (
          <>
            <Header
              title={`This element instance triggered ${instanceCount} times`}
            />
            <Content>
              To view details for any of these, select one Instance in the
              Instance History.
            </Content>
          </>
        )}

        {elementInstanceMetadata && (
          <>
            <Details
              elementInstance={elementInstanceMetadata}
              businessObject={businessObject}
              job={jobSearchResult?.[0]}
              calledProcessInstance={processInstancesSearchResult?.items?.[0]}
              messageSubscription={messageSubscriptionSearchResult?.items?.[0]}
              calledDecisionDefinition={calledDecisionDefinition}
              calledDecisionInstance={calledDecisionInstance}
              userTask={
                elementInstanceMetadata.type === 'USER_TASK'
                  ? userTask
                  : undefined
              }
            />
            {elementInstanceMetadata.hasIncident && (
              <Incidents
                elementInstanceKey={elementInstanceMetadata.elementInstanceKey}
                elementName={elementInstanceMetadata.elementName}
                elementId={elementId}
              />
            )}
          </>
        )}

        {!elementInstanceMetadata && incidentCount > 0 && (
          <>
            <Divider />
            <MultiIncidents
              count={incidentCount}
              onButtonClick={() => {
                if (IS_INCIDENTS_PANEL_V2) {
                  return incidentsPanelStore.setPanelOpen(true);
                }
                incidentsStore.clearSelection();
                incidentsStore.toggleFlowNodeSelection(elementId);
                incidentsStore.setIncidentBarOpen(true);
              }}
            />
          </>
        )}
      </Stack>
    </Popover>
  );
});

export {MetadataPopover};
