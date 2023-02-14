/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';
import {computed} from 'mobx';
import {SpinnerSkeleton} from 'modules/components/SpinnerSkeleton';
import {observer} from 'mobx-react';
import {OverlayPosition} from 'bpmn-js/lib/NavigatedViewer';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {sequenceFlowsStore} from 'modules/stores/sequenceFlows';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {incidentsStore} from 'modules/stores/incidents';
import {Diagram} from 'modules/components/Diagram';
import {IncidentsWrapper} from '../IncidentsWrapper';
import {Container, DiagramPanel, StatusMessage} from './styled';
import {IncidentsBanner} from './IncidentsBanner';
import {StatisticsOverlay} from 'modules/components/StatisticsOverlay';
import {tracking} from 'modules/tracking';
import {MetadataPopover} from './MetadataPopover';
import {modificationsStore} from 'modules/stores/modifications';
import {ModificationDropdown} from './ModificationDropdown';
import {ModificationBadgeOverlay} from './ModificationBadgeOverlay';
import {
  CANCELED_BADGE,
  MODIFICATIONS,
  ACTIVE_BADGE,
  INCIDENTS_BADGE,
  COMPLETED_BADGE,
} from 'modules/bpmn-js/badgePositions';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {ModificationInfoBanner} from './ModificationInfoBanner';

const OVERLAY_TYPE_STATE = 'flowNodeState';
const OVERLAY_TYPE_MODIFICATIONS_BADGE = 'modificationsBadge';

const overlayPositions = {
  active: ACTIVE_BADGE,
  incidents: INCIDENTS_BADGE,
  canceled: CANCELED_BADGE,
  completed: COMPLETED_BADGE,
} as const;

type ModificationBadgePayload = {
  newTokenCount: number;
  cancelledTokenCount: number;
};

const TopPanel: React.FC = observer(() => {
  const {selectableFlowNodes} = processInstanceDetailsStatisticsStore;

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

  const flowNodeStateOverlays =
    processInstanceDetailsStatisticsStore.flowNodeStatistics.map(
      ({flowNodeState, count, flowNodeId}) => ({
        payload: {flowNodeState, count},
        type: OVERLAY_TYPE_STATE,
        flowNodeId,
        position: overlayPositions[flowNodeState],
      })
    );

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
    }, [])
  );

  const {items: processedSequenceFlows} = sequenceFlowsStore.state;
  const {processInstance} = processInstanceDetailsStore.state;
  const stateOverlays = diagramOverlaysStore.state.overlays.filter(
    ({type}) => type === OVERLAY_TYPE_STATE
  );
  const modificationBadgeOverlays = diagramOverlaysStore.state.overlays.filter(
    ({type}) => type === OVERLAY_TYPE_MODIFICATIONS_BADGE
  );
  const {
    state: {status, xml},
    modifiableFlowNodes,
  } = processInstanceDetailsDiagramStore;

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

      <DiagramPanel data-testid="diagram-panel-body">
        {['initial', 'first-fetch', 'fetching'].includes(status) && (
          <SpinnerSkeleton data-testid="diagram-spinner" />
        )}
        {status === 'error' && (
          <StatusMessage variant="error">
            Diagram could not be fetched
          </StatusMessage>
        )}
        {status === 'fetched' && (
          <>
            {processInstance?.state === 'INCIDENT' && (
              <IncidentsWrapper setIsInTransition={setIsInTransition} />
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
            {xml !== null && (
              <Diagram
                xml={xml}
                selectableFlowNodes={
                  isModificationModeEnabled
                    ? modifiableFlowNodes
                    : selectableFlowNodes
                }
                selectedFlowNodeId={flowNodeSelection?.flowNodeId}
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
                highlightedSequenceFlows={processedSequenceFlows}
              >
                {stateOverlays.map((overlay) => {
                  const payload = overlay.payload as {
                    flowNodeState: FlowNodeState;
                    count: number;
                  };

                  return (
                    <StatisticsOverlay
                      key={`${overlay.flowNodeId}-${payload.flowNodeState}`}
                      flowNodeState={payload.flowNodeState}
                      count={payload.count}
                      container={overlay.container}
                      isFaded={modificationsStore.hasPendingCancelOrMoveModification(
                        overlay.flowNodeId
                      )}
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
          </>
        )}
      </DiagramPanel>
    </Container>
  );
});

export {TopPanel};
