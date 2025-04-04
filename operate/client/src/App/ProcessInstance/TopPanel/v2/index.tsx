/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {observer} from 'mobx-react';
import {useProcessInstancePageParams} from '../../useProcessInstancePageParams';
import {sequenceFlowsStore} from 'modules/stores/sequenceFlows';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {incidentsStore} from 'modules/stores/incidents';
import {IncidentsBanner} from '../IncidentsBanner';
import {tracking} from 'modules/tracking';
import {modificationsStore} from 'modules/stores/modifications';
import {Container, DiagramPanel} from '../styled';
import {IncidentsWrapper} from '../../IncidentsWrapper';
import {
  CANCELED_BADGE,
  MODIFICATIONS,
  ACTIVE_BADGE,
  INCIDENTS_BADGE,
  COMPLETED_BADGE,
  COMPLETED_END_EVENT_BADGE,
} from 'modules/bpmn-js/badgePositions';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {DiagramShell} from 'modules/components/DiagramShell';
import {computed} from 'mobx';
import {OverlayPosition} from 'bpmn-js/lib/NavigatedViewer';
import {Diagram} from 'modules/components/Diagram';
import {MetadataPopover} from '../MetadataPopover';
import {ModificationBadgeOverlay} from '../ModificationBadgeOverlay';
import {ModificationInfoBanner} from '../ModificationInfoBanner';
import {ModificationDropdown} from '../ModificationDropdown/v2';
import {StateOverlay} from 'modules/components/StateOverlay';
import {executionCountToggleStore} from 'modules/stores/executionCountToggle';
import {useFlownodeStatistics} from 'modules/queries/flownodeInstancesStatistics/useFlownodeStatistics';
import {useSelectableFlowNodes} from 'modules/queries/flownodeInstancesStatistics/useSelectableFlowNodes';
import {useExecutedFlowNodes} from 'modules/queries/flownodeInstancesStatistics/useExecutedFlowNodes';
import {useModificationsByFlowNode} from 'modules/hooks/modifications';
import {useModifiableFlowNodes} from 'modules/hooks/processInstanceDetailsDiagram';
import {getSelectedRunningInstanceCount} from 'modules/utils/flowNodeSelection';
import {
  useTotalRunningInstancesForFlowNode,
  useTotalRunningInstancesVisibleForFlowNode,
} from 'modules/queries/flownodeInstancesStatistics/useTotalRunningInstancesForFlowNode';
import {finishMovingToken} from 'modules/utils/modifications';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';
import {useFlownodeInstancesStatistics} from 'modules/queries/flownodeInstancesStatistics/useFlownodeInstancesStatistics';
import {init} from 'modules/utils/flowNodeMetadata';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {isCompensationAssociation} from 'modules/bpmn-js/utils/isCompensationAssociation';

const OVERLAY_TYPE_STATE = 'flowNodeState';
const OVERLAY_TYPE_MODIFICATIONS_BADGE = 'modificationsBadge';

const overlayPositions = {
  active: ACTIVE_BADGE,
  incidents: INCIDENTS_BADGE,
  canceled: CANCELED_BADGE,
  completed: COMPLETED_BADGE,
  completedEndEvents: COMPLETED_END_EVENT_BADGE,
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
  const [isInTransition, setIsInTransition] = useState(false);
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
  const modificationsByFlowNode = useModificationsByFlowNode();
  const affectedTokenCount = totalMoveOperationRunningInstances || 1;
  const visibleAffectedTokenCount =
    totalMoveOperationRunningInstancesVisible || 1;

  const processDefinitionKey = useProcessDefinitionKeyContext();

  const {
    data: processDefinitionData,
    isFetching: isXmlFetching,
    isError: isXmlError,
  } = useProcessInstanceXml({
    processDefinitionKey,
  });

  useEffect(() => {
    if (flowNodeInstancesStatistics?.items) {
      init(flowNodeInstancesStatistics.items);
    }
  });

  useEffect(() => {
    if (flowNodeInstancesStatistics?.items) {
      init(flowNodeInstancesStatistics.items);
    }
  });

  useEffect(() => {
    sequenceFlowsStore.init();

    return () => {
      sequenceFlowsStore.reset();
      diagramOverlaysStore.reset();
    };
  }, [processInstanceId]);

  const allFlowNodeStateOverlays = statistics?.map(
    ({flowNodeState, count, id: flowNodeId}) => ({
      payload: {flowNodeState, count},
      type: OVERLAY_TYPE_STATE,
      flowNodeId,
      position: overlayPositions[flowNodeState],
    }),
  );

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
      return executedFlowNodes?.find(({flowNodeId, completed}) => {
        return targetRef?.id === flowNodeId && completed > 0;
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

  const {items: processedSequenceFlows} = sequenceFlowsStore.state;
  const {processInstance} = processInstanceDetailsStore.state;
  const stateOverlays = diagramOverlaysStore.state.overlays.filter(
    ({type}) => type === OVERLAY_TYPE_STATE,
  );
  const modificationBadgeOverlays = diagramOverlaysStore.state.overlays.filter(
    ({type}) => type === OVERLAY_TYPE_MODIFICATIONS_BADGE,
  );

  const modifiableFlowNodes = useModifiableFlowNodes();

  const {
    setIncidentBarOpen,
    state: {isIncidentBarOpen},
    incidentsCount,
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
      {incidentsCount > 0 && (
        <IncidentsBanner
          onClick={() => {
            if (isInTransition) {
              return;
            }

            tracking.track({
              eventName: isIncidentBarOpen
                ? 'incidents-panel-closed'
                : 'incidents-panel-opened',
            });

            setIncidentBarOpen(!isIncidentBarOpen);
          }}
          isOpen={incidentsStore.state.isIncidentBarOpen}
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
                ),
              label: 'Discard',
            }}
          />
        )}
      {modificationsStore.isModificationModeEnabled &&
        getSelectedRunningInstanceCount(totalRunningInstances || 0) > 1 && (
          <ModificationInfoBanner text="Flow node has multiple instances. To select one, use the instance history tree below." />
        )}
      {modificationsStore.state.status === 'adding-token' && (
        <ModificationInfoBanner
          text="Flow node has multiple parent scopes. Please select parent node from Instance History to Add."
          button={{
            onClick: () => modificationsStore.finishAddingToken(),
            label: 'Discard',
          }}
        />
      )}
      <DiagramPanel>
        <DiagramShell status={getStatus()}>
          {processDefinitionData?.xml !== undefined && businessObjects && (
            <Diagram
              xml={processDefinitionData?.xml}
              selectableFlowNodes={
                isModificationModeEnabled
                  ? modifiableFlowNodes
                  : selectableFlowNodes
              }
              selectedFlowNodeIds={
                flowNodeSelection?.flowNodeId
                  ? [flowNodeSelection.flowNodeId]
                  : undefined
              }
              onFlowNodeSelection={(flowNodeId, isMultiInstance) => {
                if (modificationsStore.state.status === 'moving-token') {
                  flowNodeSelectionStore.clearSelection();
                  finishMovingToken(
                    affectedTokenCount,
                    visibleAffectedTokenCount,
                    businessObjects,
                    flowNodeId,
                  );
                } else {
                  if (modificationsStore.state.status !== 'adding-token') {
                    flowNodeSelectionStore.selectFlowNode({
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
                ...processedSequenceFlows,
                ...compensationAssociationIds,
              ]}
              highlightedFlowNodeIds={executedFlowNodes?.map(
                ({flowNodeId}) => flowNodeId,
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
                    isFaded={modificationsStore.hasPendingCancelOrMoveModification(
                      overlay.flowNodeId,
                    )}
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
        {processInstance?.state === 'INCIDENT' && (
          <IncidentsWrapper setIsInTransition={setIsInTransition} />
        )}
      </DiagramPanel>
    </Container>
  );
});

export {TopPanel};
