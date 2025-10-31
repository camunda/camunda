/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {observer} from 'mobx-react';
import {useProcessInstancePageParams} from '../useProcessInstancePageParams';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {incidentsStore} from 'modules/stores/incidents';
import {modificationsStore} from 'modules/stores/modifications';
import {Container, DiagramPanel} from './styled';
import {tracking} from 'modules/tracking';
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
import {Diagram} from 'modules/components/Diagram/v2';
import {MetadataPopover} from './MetadataPopover/v2';
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
  clearSelection,
  getSelectedRunningInstanceCount,
  selectFlowNode,
} from 'modules/utils/flowNodeSelection';
import {
  useTotalRunningInstancesForFlowNode,
  useTotalRunningInstancesVisibleForFlowNode,
} from 'modules/queries/flownodeInstancesStatistics/useTotalRunningInstancesForFlowNode';
import {
  finishMovingToken,
  hasPendingCancelOrMoveModification,
} from 'modules/utils/modifications';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';
import {useFlownodeInstancesStatistics} from 'modules/queries/flownodeInstancesStatistics/useFlownodeInstancesStatistics';
import {init} from 'modules/utils/flowNodeMetadata';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {isCompensationAssociation} from 'modules/bpmn-js/utils/isCompensationAssociation';
import {useProcessSequenceFlows} from 'modules/queries/sequenceFlows/useProcessSequenceFlows';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {getSubprocessOverlayFromIncidentFlowNodes} from 'modules/utils/flowNodes';
import {
  useIsRootNodeSelected,
  useRootNode,
} from 'modules/hooks/flowNodeSelection';
import type {FlowNodeState} from 'modules/types/operate';

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
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const flowNodeSelection = flowNodeSelectionStore.state.selection;
  const currentSelection = flowNodeSelectionStore.state.selection;
  const {sourceFlowNodeIdForMoveOperation} = modificationsStore.state;
  const [_isInTransition] = useState(false);
  const {data: flowNodeInstancesStatistics} = useFlownodeInstancesStatistics();
  const {data: statistics} = useFlownodeStatistics();
  const {data: selectableFlowNodes} = useSelectableFlowNodes();
  const {data: executedFlowNodes} = useExecutedFlowNodes();
  const {data: totalRunningInstances} = useTotalRunningInstancesForFlowNode(
    currentSelection?.flowNodeId,
  );
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
  const affectedTokenCount = totalMoveOperationRunningInstances || 1;
  const visibleAffectedTokenCount =
    totalMoveOperationRunningInstancesVisible || 1;
  const {data: processedSequenceFlowsFromHook} =
    useProcessSequenceFlows(processInstanceId);
  const processDefinitionKey = useProcessDefinitionKeyContext();
  const rootNode = useRootNode();
  const isRootNodeSelected = useIsRootNodeSelected();

  const {
    data: processDefinitionData,
    isFetching: isXmlFetching,
    isError: isXmlError,
  } = useProcessInstanceXml({
    processDefinitionKey,
  });

  useEffect(() => {
    if (flowNodeInstancesStatistics?.items && processInstance) {
      init(
        processInstance.processInstanceKey,
        flowNodeInstancesStatistics.items,
      );
    }
  }, [flowNodeInstancesStatistics?.items, processInstance]);

  useEffect(() => {
    return () => {
      diagramOverlaysStore.reset();
    };
  }, [processInstanceId]);

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

  const flowNodeStateOverlays = executionCountToggleStore.state
    .isExecutionCountVisible
    ? allFlowNodeStateOverlays
    : notCompletedFlowNodeStateOverlays;

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

  const {
    state: {isIncidentBarOpen},
  } = incidentsStore;

  const {isModificationModeEnabled} = modificationsStore;

  useEffect(() => {
    if (!isModificationModeEnabled) {
      if (flowNodeSelection?.flowNodeId) {
        tracking.track({
          eventName: 'metadata-popover-opened',
        });
      } else {
        tracking.track({
          eventName: 'metadata-popover-closed',
        });
      }
    }
  }, [flowNodeSelection?.flowNodeId, isModificationModeEnabled]);

  const getStatus = () => {
    if (isXmlFetching) {
      return 'loading';
    }
    if (isXmlError) {
      return 'error';
    }
    return 'content';
  };

  return (
    <Container>
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
      {modificationsStore.isModificationModeEnabled &&
        getSelectedRunningInstanceCount({
          totalRunningInstancesForFlowNode: totalRunningInstances ?? 0,
          isRootNodeSelected,
        }) > 1 && (
          <ModificationInfoBanner text="Flow node has multiple instances. To select one, use the instance history tree below." />
        )}
      {modificationsStore.state.status === 'adding-token' &&
        businessObjects && (
          <ModificationInfoBanner
            text="Flow node has multiple parent scopes. Please select parent node from Instance History to Add."
            button={{
              onClick: () =>
                modificationsStore.finishAddingToken(businessObjects),
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
                selectableFlowNodes={
                  isModificationModeEnabled
                    ? modifiableFlowNodes
                    : selectableFlowNodes
                }
                selectedFlowNodeIds={
                  flowNodeSelection?.anchorFlowNodeId
                    ? [flowNodeSelection.anchorFlowNodeId]
                    : flowNodeSelection?.flowNodeId
                      ? [flowNodeSelection.flowNodeId]
                      : undefined
                }
                onFlowNodeSelection={(flowNodeId, isMultiInstance) => {
                  if (modificationsStore.state.status === 'moving-token') {
                    clearSelection(rootNode);
                    finishMovingToken(
                      affectedTokenCount,
                      visibleAffectedTokenCount,
                      businessObjects,
                      processInstance?.processDefinitionId,
                      flowNodeId,
                    );
                  } else {
                    if (modificationsStore.state.status !== 'adding-token') {
                      selectFlowNode(rootNode, {
                        flowNodeId,
                        isMultiInstance,
                      });
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
                highlightedSequenceFlows={[
                  ...(processedSequenceFlowsFromHook || []),
                  ...compensationAssociationIds,
                ]}
                highlightedFlowNodeIds={executedFlowNodes?.map(
                  ({elementId}) => elementId,
                )}
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
        {/* Incidents panel is rendered as page rightPanel to ensure proper overlay layering */}
      </DiagramPanel>
    </Container>
  );
});

export {TopPanel};
