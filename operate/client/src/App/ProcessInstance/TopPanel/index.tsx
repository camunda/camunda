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
import {sequenceFlowsStore} from 'modules/stores/sequenceFlows';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {incidentsStore} from 'modules/stores/incidents';
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
} from 'modules/bpmn-js/badgePositions';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {DiagramShell} from 'modules/components/DiagramShell';
import {computed} from 'mobx';
import {OverlayPosition} from 'bpmn-js/lib/NavigatedViewer';
import {Diagram} from 'modules/components/Diagram';
import {MetadataPopover} from './MetadataPopover';
import {ModificationBadgeOverlay} from './ModificationBadgeOverlay';
import {ModificationInfoBanner} from './ModificationInfoBanner';
import {ModificationDropdown} from './ModificationDropdown';
import {StateOverlay} from 'modules/components/StateOverlay';
import {executionCountToggleStore} from 'modules/stores/executionCountToggle';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';

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
  const {selectableFlowNodes, executedFlowNodes} =
    processInstanceDetailsStatisticsStore;
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const flowNodeSelection = flowNodeSelectionStore.state.selection;
  const [isInTransition, setIsInTransition] = useState(false);

  useEffect(() => {
    sequenceFlowsStore.init();
    flowNodeMetaDataStore.init();

    return () => {
      sequenceFlowsStore.reset();
      flowNodeMetaDataStore.reset();
      diagramOverlaysStore.reset();
    };
  }, [processInstanceId]);

  const processDefinitionKey = useProcessDefinitionKeyContext();

  const {
    data: processDefinitionData,
    isFetching: isXmlFetching,
    isError: isXmlError,
  } = useProcessInstanceXml({
    processDefinitionKey,
    processInstanceStatistics:
      processInstanceDetailsStatisticsStore.state.statistics,
    getTotalRunningInstancesForFlowNode:
      processInstanceDetailsStatisticsStore.getTotalRunningInstancesForFlowNode,
  });

  const allFlowNodeStateOverlays =
    processInstanceDetailsStatisticsStore.flowNodeStatistics.map(
      ({flowNodeState, count, flowNodeId}) => ({
        payload: {flowNodeState, count},
        type: OVERLAY_TYPE_STATE,
        flowNodeId,
        position: overlayPositions[flowNodeState],
      }),
    );

  const notCompletedFlowNodeStateOverlays = allFlowNodeStateOverlays.filter(
    (stateOverlay) => stateOverlay.payload.flowNodeState !== 'completed',
  );

  const flowNodeStateOverlays = executionCountToggleStore.state
    .isExecutionCountVisible
    ? allFlowNodeStateOverlays
    : notCompletedFlowNodeStateOverlays;

  const compensationAssociationIds =
    processDefinitionData?.compensationAssociations
      .filter(({targetRef}) => {
        // check if the target element for the association was executed
        return executedFlowNodes.find(({activityId, completed}) => {
          return targetRef?.id === activityId && completed > 0;
        });
      })
      .map(({id}) => id) ?? [];

  const modificationBadgesPerFlowNode = computed(() =>
    Object.entries(modificationsStore.modificationsByFlowNode).reduce<
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
      {modificationsStore.state.status === 'moving-token' && (
        <ModificationInfoBanner
          text="Select the target flow node in the diagram"
          button={{
            onClick: () => modificationsStore.finishMovingToken(),
            label: 'Discard',
          }}
        />
      )}
      {modificationsStore.isModificationModeEnabled &&
        flowNodeSelectionStore.selectedRunningInstanceCount > 1 && (
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
          {processDefinitionData?.xml !== undefined && (
            <Diagram
              xml={processDefinitionData.xml}
              selectableFlowNodes={
                isModificationModeEnabled
                  ? processDefinitionData?.modifiableFlowNodes
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
                  modificationsStore.finishMovingToken(flowNodeId);
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
                      ...flowNodeStateOverlays,
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
              highlightedFlowNodeIds={executedFlowNodes.map(
                ({activityId}) => activityId,
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
