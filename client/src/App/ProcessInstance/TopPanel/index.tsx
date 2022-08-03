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

const OVERLAY_TYPE = 'flowNodeState';

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
            type: OVERLAY_TYPE,
            position: {bottom: 17, left: -7},
            payload: {state},
          },
        ];
      }
    }, [])
  );

  const {items: processedSequenceFlows} = sequenceFlowsStore.state;
  const {processInstance} = processInstanceDetailsStore.state;
  const stateOverlays = diagramOverlaysStore.state.overlays.filter(
    ({type}) => type === OVERLAY_TYPE
  );
  const {
    state: {status, xml},
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

            {xml !== null && (
              <Diagram
                xml={xml}
                selectableFlowNodes={selectableFlowNodes}
                selectedFlowNodeId={flowNodeSelection?.flowNodeId}
                onFlowNodeSelection={(flowNodeId, isMultiInstance) => {
                  flowNodeSelectionStore.selectFlowNode({
                    flowNodeId,
                    isMultiInstance,
                  });
                }}
                overlaysData={flowNodeStateOverlays.get()}
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
              </Diagram>
            )}
          </>
        )}
      </DiagramPanel>
    </Container>
  );
});

export {TopPanel};
