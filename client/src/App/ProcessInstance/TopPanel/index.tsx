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
import {flowNodeStatesStore} from 'modules/stores/flowNodeStates';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {incidentsStore} from 'modules/stores/incidents';
import {Diagram} from 'modules/components/Diagram';
import {IncidentsWrapper} from '../IncidentsWrapper';
import {Container, DiagramPanel, StatusMessage} from './styled';
import {IncidentsBanner} from './IncidentsBanner';
import {StateOverlay} from './StateOverlay';
import {tracking} from 'modules/tracking';
import {MetadataPopover} from './MetadataPopover';
import {modificationsStore} from 'modules/stores/modifications';
import {ModificationDropdown} from './ModificationDropdown';
import {MoveTokenBanner} from './MoveTokenBanner';
import {ModificationBadgeOverlay} from './ModificationBadgeOverlay';
import {MODIFICATIONS, FLOW_NODE_STATE} from 'modules/bpmn-js/badgePositions';

const OVERLAY_TYPE_STATE = 'flowNodeState';
const OVERLAY_TYPE_MODIFICATIONS_BADGE = 'modificationsBadge';

type ModificationBadgePayload = {
  newTokenCount: number;
  cancelledTokenCount: number;
};

type Props = {
  incidents?: unknown;
  children?: React.ReactNode;
};

const TopPanel: React.FC<Props> = observer(() => {
  const {
    selectableFlowNodes,
    state: {flowNodes},
  } = flowNodeStatesStore;

  const {processInstanceId = ''} = useProcessInstancePageParams();
  const flowNodeSelection = flowNodeSelectionStore.state.selection;
  const [isInTransition, setIsInTransition] = useState(false);

  useEffect(() => {
    sequenceFlowsStore.init();
    flowNodeMetaDataStore.init();
    flowNodeStatesStore.init(processInstanceId);

    return () => {
      sequenceFlowsStore.reset();
      flowNodeStatesStore.reset();
      flowNodeMetaDataStore.reset();
      diagramOverlaysStore.reset();
    };
  }, [processInstanceId]);

  const flowNodeStateOverlays = computed(() =>
    Object.entries(flowNodes).reduce<
      {
        flowNodeId: string;
        type: string;
        payload: {state: InstanceEntityState};
        position: OverlayPosition;
      }[]
    >((flowNodeStates, [flowNodeId, state]) => {
      const metaData =
        processInstanceDetailsDiagramStore.getMetaData(flowNodeId);

      if (state === 'COMPLETED' && metaData?.type.elementType !== 'END') {
        return flowNodeStates;
      } else {
        return [
          ...flowNodeStates,
          {
            flowNodeId,
            type: OVERLAY_TYPE_STATE,
            position: FLOW_NODE_STATE,
            payload: {state},
          },
        ];
      }
    }, [])
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
            cancelledTokenCount: tokens.cancelledTokens,
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
              <MoveTokenBanner />
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
                    modificationsStore.finishMovingToken();
                  } else {
                    flowNodeSelectionStore.selectFlowNode({
                      flowNodeId,
                      isMultiInstance,
                    });
                  }
                }}
                overlaysData={
                  isModificationModeEnabled
                    ? [
                        ...flowNodeStateOverlays.get(),
                        ...modificationBadgesPerFlowNode.get(),
                      ]
                    : flowNodeStateOverlays.get()
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
                {stateOverlays?.map((overlay) => {
                  const payload = overlay.payload as {
                    state: InstanceEntityState;
                  };

                  return (
                    <StateOverlay
                      key={overlay.flowNodeId}
                      state={payload.state}
                      container={overlay.container}
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
