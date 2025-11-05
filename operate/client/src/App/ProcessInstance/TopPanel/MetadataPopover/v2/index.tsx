/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Divider, Popover, Content} from '../styled';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {incidentsStore} from 'modules/stores/incidents';
import {observer} from 'mobx-react';
import {flip, offset} from '@floating-ui/react-dom';
import {Header} from '../Header';
import {Loading, Stack} from '@carbon/react';
import {Incident} from './Incident';
import {MultiIncidents} from '../MultiIncidents';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useElementInstancesSearch} from 'modules/queries/elementInstances/useElementInstancesSearch';
import {useElementInstance} from 'modules/queries/elementInstances/useElementInstance';
import {useFlownodeInstancesStatistics} from 'modules/queries/flownodeInstancesStatistics/useFlownodeInstancesStatistics';
import {useMemo} from 'react';
import {Details} from './Details';
import {createV2InstanceMetadata} from './types';
import {useGetUserTaskByElementInstance} from 'modules/queries/userTasks/useGetUserTaskByElementInstance';
import {useGetIncidentsByProcessInstance} from 'modules/queries/incidents/useGetIncidentsByProcessInstance';
import {useProcessInstancesSearch} from 'modules/queries/processInstance/useProcessInstancesSearch';
import {resolveIncidentErrorType} from './Incident/resolveIncidentErrorType';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';
import {convertBpmnJsTypeToAPIType} from './convertBpmnJsTypeToAPIType';
import {useJobs} from 'modules/queries/jobs/useJobs';
import {useSearchMessageSubscriptions} from 'modules/queries/messageSubscriptions/useSearchMessageSubscriptions';
import {useDecisionInstancesSearch} from 'modules/queries/decisionInstances/useDecisionInstancesSearch';
import {useDecisionDefinition} from 'modules/queries/decisionDefinitions/useDecisionDefinition';
import {incidentsPanelStore} from 'modules/stores/incidentsPanel';
import {IS_INCIDENTS_PANEL_V2} from 'modules/feature-flags';

type Props = {
  selectedFlowNodeRef?: SVGGraphicsElement | null;
};

const MetadataPopover = observer(({selectedFlowNodeRef}: Props) => {
  const {data: processInstance} = useProcessInstance();
  const selection = flowNodeSelectionStore.state.selection;
  const elementId = selection?.flowNodeId;
  const elementInstanceId = selection?.flowNodeInstanceId;
  const isMultiInstance = selection?.isMultiInstance ?? false;
  const {metaData} = flowNodeMetaDataStore.state;

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

  const shouldFetchElementInstances =
    instanceCount === 1 &&
    !elementInstanceId &&
    !!processInstance?.processInstanceKey &&
    !!elementId;

  const {data: elementInstance, isLoading: isFetchingInstance} =
    useElementInstance(elementInstanceId ?? '', {
      enabled: !!elementInstanceId && !!elementId,
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
    if (elementInstanceId && elementInstance) {
      return elementInstance;
    }

    if (
      !elementInstanceId &&
      instanceCount === 1 &&
      elementInstancesSearchResult?.items?.length === 1
    ) {
      return elementInstancesSearchResult.items[0];
    }

    return null;
  }, [
    elementInstanceId,
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

  const shouldFilterByElementInstance =
    !!elementInstanceMetadata?.elementInstanceKey &&
    elementInstanceMetadata?.type !== 'CALL_ACTIVITY' &&
    elementInstanceMetadata?.type !== 'BUSINESS_RULE_TASK';

  const {
    data: elementInstancesIncidentsSearchResult,
    isLoading: isSearchingIncidents,
  } = useGetIncidentsByProcessInstance(
    processInstance?.processInstanceKey ?? '',
    {
      select: (result) => {
        const elementInstanceKey = elementInstanceMetadata?.elementInstanceKey;
        return shouldFilterByElementInstance && elementInstanceKey
          ? result.items.filter(
              (incident) => incident.elementInstanceKey === elementInstanceKey,
            )
          : result.items;
      },
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

  const singleIncident =
    elementInstancesIncidentsSearchResult?.length === 1
      ? elementInstancesIncidentsSearchResult[0]
      : null;

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
    metaData === null ||
    (shouldFetchElementInstances && isSearchingElementInstances) ||
    (!!elementInstanceId && isFetchingInstance) ||
    isSearchingUserTasks ||
    isSearchingProcessInstances ||
    isSearchingJob ||
    isSearchingMessageSubscription ||
    isSearchingCalledDecisionDefinition ||
    isSearchingDecisionInstances
  ) {
    return null;
  }

  const {instanceMetadata, incident} = metaData;

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
        {instanceCount !== null && instanceCount > 1 && !elementInstanceId && (
          <>
            <Header
              title={`This Element instance triggered ${instanceCount} times`}
            />
            <Content>
              To view details for any of these, select one Instance in the
              Instance History.
            </Content>
          </>
        )}

        {elementInstanceMetadata && (
          <Details
            metaData={{
              ...metaData,
              instanceMetadata: createV2InstanceMetadata(
                instanceMetadata,
                elementInstanceMetadata,
                jobSearchResult?.[0],
                processInstancesSearchResult?.items?.[0],
                messageSubscriptionSearchResult?.items?.[0],
                calledDecisionDefinition,
                calledDecisionInstance,
                elementInstanceMetadata.type === 'USER_TASK'
                  ? userTask
                  : undefined,
              ),
              incident: singleIncident
                ? {
                    errorType: resolveIncidentErrorType(
                      singleIncident?.errorType,
                    ),
                    errorMessage: singleIncident.errorMessage,
                  }
                : null,
            }}
            elementId={elementInstanceMetadata.elementId}
            businessObject={businessObject}
          />
        )}
        {isSearchingIncidents ? (
          <Loading small withOverlay={false} data-testid="incidents-loading" />
        ) : singleIncident ? (
          <>
            <Divider />
            <Incident
              processInstanceId={processInstance?.processInstanceKey}
              incidentV2={singleIncident}
              incident={incident}
              onButtonClick={() => {
                if (IS_INCIDENTS_PANEL_V2 && elementInstanceMetadata) {
                  return incidentsPanelStore.showIncidentsForElementInstance(
                    elementInstanceMetadata.elementInstanceKey,
                    elementInstanceMetadata.elementName,
                  );
                }
                incidentsStore.clearSelection();
                incidentsStore.toggleFlowNodeSelection(elementId);
                incidentsStore.toggleErrorTypeSelection(
                  singleIncident.errorType,
                );
                incidentsStore.setIncidentBarOpen(true);
              }}
            />
          </>
        ) : elementInstancesIncidentsSearchResult &&
          elementInstancesIncidentsSearchResult?.length > 1 ? (
          <>
            <Divider />
            <MultiIncidents
              count={elementInstancesIncidentsSearchResult?.length}
              onButtonClick={() => {
                if (IS_INCIDENTS_PANEL_V2 && elementInstanceMetadata) {
                  return incidentsPanelStore.showIncidentsForElementInstance(
                    elementInstanceMetadata.elementInstanceKey,
                    elementInstanceMetadata.elementName,
                  );
                }
                incidentsStore.clearSelection();
                incidentsStore.toggleFlowNodeSelection(elementId);
                incidentsStore.setIncidentBarOpen(true);
              }}
            />
          </>
        ) : null}
      </Stack>
    </Popover>
  );
});

export {MetadataPopover};
