/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useState} from 'react';
import {observer} from 'mobx-react';
import {useProcessInstancePageParams} from '../useProcessInstancePageParams';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {IncidentsBanner} from './IncidentsBanner';
import {tracking} from 'modules/tracking';
import {modificationsStore} from 'modules/stores/modifications';
import {Container, DiagramPanel} from './styled';
import {IncidentsWrapper} from '../IncidentsWrapper';
import {
  CANCELED_BADGE,
  MODIFICATIONS,
  ACTIVE_BADGE,
  INCIDENTS_BADGE,
  COMPLETED_BADGE,
  COMPLETED_END_EVENT_BADGE,
  SUBPROCESS_WITH_INCIDENTS,
} from 'modules/bpmn-js/badgePositions';
import {DiagramShell} from 'modules/components/DiagramShell';
import {computed} from 'mobx';
import {type OverlayPosition} from 'bpmn-js/lib/NavigatedViewer';
import {Diagram} from 'modules/components/Diagram';
import {MetadataPopover} from './MetadataPopover';
import {ModificationBadgeOverlay} from './ModificationBadgeOverlay';
import {ModificationInfoBanner} from './ModificationInfoBanner';
import {ModificationDropdown} from './ModificationDropdown';
import {StateOverlay} from 'modules/components/StateOverlay';
import {executionCountToggleStore} from 'modules/stores/executionCountToggle';
import {useFlownodeStatistics} from 'modules/queries/flownodeInstancesStatistics/useFlownodeStatistics';
import {useSelectableFlowNodes} from 'modules/queries/flownodeInstancesStatistics/useSelectableFlowNodes';
import {useExecutedFlowNodes} from 'modules/queries/flownodeInstancesStatistics/useExecutedFlowNodes';
import {useModificationsByFlowNode} from 'modules/hooks/modifications';
import {useModifiableFlowNodes} from 'modules/hooks/processInstanceDetailsDiagram';
import {
  useTotalRunningInstancesByFlowNode,
  useTotalRunningInstancesForFlowNode,
  useTotalRunningInstancesVisibleForFlowNode,
} from 'modules/queries/flownodeInstancesStatistics/useTotalRunningInstancesForFlowNode';
import {
  finishMovingToken,
  hasPendingCancelOrMoveModification,
} from 'modules/utils/modifications';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {isCompensationAssociation} from 'modules/bpmn-js/utils/isCompensationAssociation';
import {useProcessSequenceFlows} from 'modules/queries/sequenceFlows/useProcessSequenceFlows';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {getSubprocessOverlayFromIncidentFlowNodes} from 'modules/utils/flowNodes';
import type {FlowNodeState} from 'modules/types/operate';
import {HTTP_STATUS_FORBIDDEN} from 'modules/constants/statusCode';
import {isRequestError} from 'modules/request';
import {useProcessInstanceIncidentsCount} from 'modules/queries/incidents/useProcessInstanceIncidentsCount';
import {incidentsPanelStore} from 'modules/stores/incidentsPanel';
import {isInstanceRunning} from 'modules/utils/instance';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {getAncestorScopeType} from 'modules/utils/processInstanceDetailsDiagram';

const OVERLAY_TYPE_STATE = 'flowNodeState';
const OVERLAY_TYPE_MODIFICATIONS_BADGE = 'modificationsBadge';

const overlayPositions = {
  active: ACTIVE_BADGE,
  incidents: INCIDENTS_BADGE,
  canceled: CANCELED_BADGE,
  completed: COMPLETED_BADGE,
  completedEndEvents: COMPLETED_END_EVENT_BADGE,
  subprocessWithIncidents: SUBPROCESS_WITH_INCIDENTS,
} as const;

type ModificationBadgePayload = {
  newTokenCount: number;
  cancelledTokenCount: number;
};

const TopPanel: React.FC = observer(() => {
  const {
    clearSelection,
    selectedElementId,
    selectedElementInstanceKey,
    selectElement,
    selectedAnchorElementId,
  } = useProcessInstanceElementSelection();
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const {
    sourceFlowNodeIdForMoveOperation,
    sourceFlowNodeInstanceKeyForMoveOperation,
  } = modificationsStore.state;
  const [isInTransition, setIsInTransition] = useState(false);
  const {data: statistics} = useFlownodeStatistics();
  const {data: selectableFlowNodes} = useSelectableFlowNodes();
  const {data: executedFlowNodes} = useExecutedFlowNodes();
  const {data: totalRunningInstancesByFlowNode} =
    useTotalRunningInstancesByFlowNode();
  const {data: businessObjects} = useBusinessObjects();
  const {data: totalMoveOperationRunningInstances} =
    useTotalRunningInstancesForFlowNode(
      sourceFlowNodeIdForMoveOperation || undefined,
    );
  const {data: totalMoveOperationRunningInstancesVisible} =
    useTotalRunningInstancesVisibleForFlowNode(
      sourceFlowNodeIdForMoveOperation || undefined,
    );
  const {data: processInstance} = useProcessInstance();
  const modificationsByFlowNode = useModificationsByFlowNode();
  const affectedTokenCount = sourceFlowNodeInstanceKeyForMoveOperation
    ? 1
    : totalMoveOperationRunningInstances || 1;
  const visibleAffectedTokenCount = sourceFlowNodeInstanceKeyForMoveOperation
    ? 1
    : totalMoveOperationRunningInstancesVisible || 1;

  const {data: processedSequenceFlowsFromHook} =
    useProcessSequenceFlows(processInstanceId);
  const processDefinitionKey = useProcessDefinitionKeyContext();
  const {isExecutionCountVisible} = executionCountToggleStore.state;

  const {data: selectedElementRunningInstancesCount} =
    useTotalRunningInstancesForFlowNode(selectedElementId ?? undefined);
  const hasSelectedElementMultipleRunningInstances =
    selectedElementInstanceKey === null &&
    (selectedElementRunningInstancesCount ?? 0) > 1;

  const {
    data: processDefinitionData,
    isPending: isXmlFetching,
    isError: isXmlError,
    error: xmlError,
  } = useProcessInstanceXml({processDefinitionKey});

  useEffect(() => {
    return () => {
      diagramOverlaysStore.reset();
    };
  }, [processInstanceId]);

  const flowNodeStateOverlays = useMemo(() => {
    const flowNodeIdsWithIncidents = statistics
      ?.filter(({flowNodeState}) => flowNodeState === 'incidents')
      ?.map((flowNode) => flowNode.id);

    const selectableFlowNodesWithIncidents = flowNodeIdsWithIncidents?.map(
      (flowNodeId) => businessObjects?.[flowNodeId],
    );

    const subprocessOverlays = getSubprocessOverlayFromIncidentFlowNodes(
      selectableFlowNodesWithIncidents,
    );

    const allFlowNodeStateOverlays = [
      ...(statistics?.map(({flowNodeState, count, id: flowNodeId}) => ({
        payload: {flowNodeState, count},
        type: OVERLAY_TYPE_STATE,
        flowNodeId,
        position: overlayPositions[flowNodeState],
      })) || []),
      ...subprocessOverlays,
    ];

    const notCompletedFlowNodeStateOverlays = allFlowNodeStateOverlays?.filter(
      (stateOverlay) => stateOverlay.payload.flowNodeState !== 'completed',
    );

    return isExecutionCountVisible
      ? allFlowNodeStateOverlays
      : notCompletedFlowNodeStateOverlays;
  }, [statistics, businessObjects, isExecutionCountVisible]);

  const selectedElementIds = useMemo(() => {
    return selectedAnchorElementId
      ? [selectedAnchorElementId]
      : selectedElementId
        ? [selectedElementId]
        : undefined;
  }, [selectedElementId, selectedAnchorElementId]);

  const highlightedSequenceFlows = useMemo(() => {
    const compensationAssociationIds = Object.values(
      processDefinitionData?.diagramModel.elementsById ?? {},
    )
      .filter(isCompensationAssociation)
      .filter(({targetRef}) => {
        // check if the target element for the association was executed
        return executedFlowNodes?.find(({elementId, completed}) => {
          return targetRef?.id === elementId && completed > 0;
        });
      })
      .map(({id}) => id);

    return [
      ...(processedSequenceFlowsFromHook || []),
      ...compensationAssociationIds,
    ];
  }, [
    processedSequenceFlowsFromHook,
    processDefinitionData,
    executedFlowNodes,
  ]);

  const highlightedSequenceFlowIds = useMemo(() => {
    return executedFlowNodes?.map(({elementId}) => elementId);
  }, [executedFlowNodes]);

  const modificationBadgesPerFlowNode = computed(() =>
    Object.entries(modificationsByFlowNode).reduce<
      {
        flowNodeId: string;
        type: string;
        payload: ModificationBadgePayload;
        position: OverlayPosition;
      }[]
    >((badges, [flowNodeId, tokens]) => {
      return [
        ...badges,
        {
          flowNodeId,
          type: OVERLAY_TYPE_MODIFICATIONS_BADGE,
          position: MODIFICATIONS,
          payload: {
            newTokenCount: tokens.newTokens,
            cancelledTokenCount: tokens.visibleCancelledTokens,
          },
        },
      ];
    }, []),
  );

  const stateOverlays = diagramOverlaysStore.state.overlays.filter(
    ({type}) => type === OVERLAY_TYPE_STATE,
  );
  const modificationBadgeOverlays = diagramOverlaysStore.state.overlays.filter(
    ({type}) => type === OVERLAY_TYPE_MODIFICATIONS_BADGE,
  );

  const modifiableFlowNodes = useModifiableFlowNodes();

  const incidentsCount = useProcessInstanceIncidentsCount(processInstanceId, {
    enabled:
      processInstance &&
      isInstanceRunning(processInstance) &&
      !!processInstance.hasIncident,
  });
  const isIncidentBarOpen = incidentsPanelStore.state.isPanelVisible;

  const {isModificationModeEnabled} = modificationsStore;

  useEffect(() => {
    if (!isModificationModeEnabled) {
      if (selectedElementId) {
        tracking.track({eventName: 'metadata-popover-opened'});
      } else {
        tracking.track({eventName: 'metadata-popover-closed'});
      }
    }
  }, [isModificationModeEnabled, selectedElementId]);

  const getStatus = () => {
    if (isXmlFetching) {
      return 'loading';
    }
    if (
      isRequestError(xmlError) &&
      xmlError?.response?.status === HTTP_STATUS_FORBIDDEN
    ) {
      return 'forbidden';
    }
    if (isXmlError) {
      return 'error';
    }
    return 'content';
  };

  return (
    <Container>
      {incidentsCount > 0 && (
        <IncidentsBanner
          processInstanceKey={processInstanceId}
          incidentsCount={incidentsCount}
          onClick={() => {
            if (isInTransition) {
              return;
            }

            tracking.track({
              eventName: isIncidentBarOpen
                ? 'incidents-panel-closed'
                : 'incidents-panel-opened',
            });
            incidentsPanelStore.setPanelOpen(!isIncidentBarOpen);
          }}
          isOpen={isIncidentBarOpen}
        />
      )}
      {modificationsStore.state.status === 'moving-token' &&
        businessObjects && (
          <ModificationInfoBanner
            text="Select the target flow node in the diagram"
            button={{
              onClick: () =>
                finishMovingToken(
                  affectedTokenCount,
                  visibleAffectedTokenCount,
                  businessObjects,
                  processInstance?.processDefinitionId,
                ),
              label: 'Discard',
            }}
          />
        )}
      <DiagramPanel>
        <DiagramShell status={getStatus()}>
          {processDefinitionData?.xml !== undefined &&
            businessObjects &&
            processInstance && (
              <Diagram
                xml={processDefinitionData?.xml}
                processDefinitionKey={processDefinitionKey}
                selectableFlowNodes={
                  isModificationModeEnabled
                    ? modifiableFlowNodes
                    : selectableFlowNodes
                }
                selectedFlowNodeIds={selectedElementIds}
                onRootChange={(rootElementId, getSelectionRootId) => {
                  if (!selectedElementId) {
                    return;
                  }

                  if (rootElementId !== getSelectionRootId(selectedElementId)) {
                    clearSelection();
                  }
                }}
                onFlowNodeSelection={(flowNodeId, isMultiInstance) => {
                  if (modificationsStore.state.status === 'moving-token') {
                    const ancestorScopeType = getAncestorScopeType(
                      businessObjects,
                      sourceFlowNodeIdForMoveOperation ?? '',
                      flowNodeId ?? '',
                      totalRunningInstancesByFlowNode,
                    );

                    clearSelection();
                    finishMovingToken(
                      affectedTokenCount,
                      visibleAffectedTokenCount,
                      businessObjects,
                      processInstance?.processDefinitionId,
                      flowNodeId,
                      ancestorScopeType,
                    );
                  } else {
                    if (modificationsStore.state.status !== 'adding-token') {
                      if (flowNodeId !== undefined) {
                        selectElement({
                          elementId: flowNodeId,
                          isMultiInstanceBody: isMultiInstance,
                        });
                      } else {
                        clearSelection();
                      }
                    }
                  }
                }}
                overlaysData={
                  isModificationModeEnabled
                    ? [
                        ...(flowNodeStateOverlays ?? []),
                        ...modificationBadgesPerFlowNode.get(),
                      ]
                    : flowNodeStateOverlays
                }
                selectedFlowNodeOverlay={
                  isModificationModeEnabled ? (
                    <ModificationDropdown />
                  ) : (
                    !isIncidentBarOpen && <MetadataPopover />
                  )
                }
                highlightedSequenceFlows={highlightedSequenceFlows}
                highlightedFlowNodeIds={highlightedSequenceFlowIds}
                nonSelectableNodeTooltipText={
                  isModificationModeEnabled
                    ? 'Modification is not supported for this flow node.'
                    : undefined
                }
                hasOuterBorderOnSelection={
                  !isModificationModeEnabled ||
                  hasSelectedElementMultipleRunningInstances
                }
              >
                {stateOverlays.map((overlay) => {
                  const payload = overlay.payload as {
                    flowNodeState: FlowNodeState | 'completedEndEvents';
                    count: number;
                  };

                  return (
                    <StateOverlay
                      key={`${overlay.flowNodeId}-${payload.flowNodeState}`}
                      state={payload.flowNodeState}
                      count={payload.count}
                      container={overlay.container}
                      isFaded={hasPendingCancelOrMoveModification({
                        flowNodeId: overlay.flowNodeId,
                        flowNodeInstanceKey: undefined,
                        modificationsByFlowNode,
                      })}
                      title={
                        payload.flowNodeState === 'completed'
                          ? 'Execution Count'
                          : undefined
                      }
                    />
                  );
                })}
                {modificationBadgeOverlays?.map((overlay) => {
                  const payload = overlay.payload as ModificationBadgePayload;

                  return (
                    <ModificationBadgeOverlay
                      key={overlay.flowNodeId}
                      container={overlay.container}
                      newTokenCount={payload.newTokenCount}
                      cancelledTokenCount={payload.cancelledTokenCount}
                    />
                  );
                })}
              </Diagram>
            )}
        </DiagramShell>
        {processInstance?.hasIncident && (
          <IncidentsWrapper
            setIsInTransition={setIsInTransition}
            processInstance={processInstance}
          />
        )}
      </DiagramPanel>
    </Container>
  );
});

export {TopPanel};
