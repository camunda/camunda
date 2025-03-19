/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useEffect, useRef} from 'react';
import {useLocation, useNavigate, Location} from 'react-router-dom';
import {observer} from 'mobx-react';
import {useOperationsPanelResize} from 'modules/hooks/useOperationsPanelResize';
import {deleteSearchParams} from 'modules/utils/filter';
import {getProcessInstanceFilters} from 'modules/utils/filter/getProcessInstanceFilters';
import {COLLAPSABLE_PANEL_MIN_WIDTH} from 'modules/constants';
import {processesStore} from 'modules/stores/processes/processes.list';
import {Section} from '../styled';
import {DiagramShell} from 'modules/components/DiagramShell';
import {Diagram} from 'modules/components/Diagram';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {StateOverlay} from 'modules/components/StateOverlay';
import {processXmlStore} from 'modules/stores/processXml/processXml.list';
import {batchModificationStore} from 'modules/stores/batchModification';
import {isMoveModificationTarget} from 'modules/bpmn-js/utils/isMoveModificationTarget';
import {ModificationBadgeOverlay} from 'App/ProcessInstance/TopPanel/ModificationBadgeOverlay';
import {BatchModificationNotification} from '../BatchModificationNotification/v2';
import {DiagramHeader} from '../DiagramHeader';
import {useProcessInstancesOverlayData} from 'modules/queries/processInstancesStatistics/useOverlayData';
import {useBatchModificationOverlayData} from 'modules/queries/processInstancesStatistics/useBatchModificationOverlayData';

const OVERLAY_TYPE_BATCH_MODIFICATIONS_BADGE = 'batchModificationsBadge';

type ModificationBadgePayload = {
  newTokenCount: number;
  cancelledTokenCount: number;
};

function setSearchParam(
  location: Location,
  [key, value]: [key: string, value: string],
) {
  const params = new URLSearchParams(location.search);

  params.set(key, value);

  return {
    ...location,
    search: params.toString(),
  };
}

const DiagramPanel: React.FC = observer(() => {
  const navigate = useNavigate();
  const location = useLocation();
  const {process, version, flowNodeId, tenant} = getProcessInstanceFilters(
    location.search,
  );

  const isVersionSelected = version !== undefined && version !== 'all';

  const processDetails = processesStore.getSelectedProcessDetails();
  const {processName} = processDetails;

  const statisticsOverlays = diagramOverlaysStore.state.overlays.filter(
    ({type}) => type.match(/^statistics/) !== null,
  );

  const batchModificationBadgeOverlays =
    diagramOverlaysStore.state.overlays.filter(
      ({type}) => type === OVERLAY_TYPE_BATCH_MODIFICATIONS_BADGE,
    );

  const processId = processesStore.getProcessId({process, tenant, version});

  const {selectedTargetFlowNodeId} = batchModificationStore.state;

  useEffect(() => {
    return () => {
      processXmlStore.reset();
    };
  }, []);

  useEffect(() => {
    if (processId === undefined) {
      processXmlStore.reset();
      return;
    }

    const fetchDiagram = async () => {
      await processXmlStore.fetchProcessXml(processId);
    };

    fetchDiagram();
  }, [processId, location.search]);

  const {xml} = processXmlStore.state;

  const panelHeaderRef = useRef<HTMLDivElement>(null);

  useOperationsPanelResize(panelHeaderRef, (target, width) => {
    target.style['marginRight'] =
      `calc(${width}px - ${COLLAPSABLE_PANEL_MIN_WIDTH})`;
  });

  const {data: overlayData} = useProcessInstancesOverlayData(
    {},
    processId !== undefined,
  );

  const {data: batchOverlayData} = useBatchModificationOverlayData(
    {},
    {
      sourceFlowNodeId: flowNodeId,
      targetFlowNodeId: selectedTargetFlowNodeId ?? undefined,
    },
    processId !== undefined && batchModificationStore.state.isEnabled,
  );

  const isDiagramLoading =
    processXmlStore.state.status === 'fetching' ||
    !processesStore.isInitialLoadComplete ||
    (processesStore.state.status === 'fetching' &&
      location.state?.refreshContent);

  const getStatus = () => {
    if (isDiagramLoading) {
      return 'loading';
    }
    if (processXmlStore.state.status === 'error') {
      return 'error';
    }
    if (!isVersionSelected) {
      return 'empty';
    }
    return 'content';
  };

  return (
    <Section aria-label="Diagram Panel">
      <DiagramHeader
        processDetails={processDetails}
        processDefinitionId={processId}
        isVersionSelected={isVersionSelected}
        panelHeaderRef={panelHeaderRef}
      />
      <DiagramShell
        status={getStatus()}
        emptyMessage={
          version === 'all'
            ? {
                message: `There is more than one Version selected for Process "${processName}"`,
                additionalInfo: 'To see a Diagram, select a single Version',
              }
            : {
                message: 'There is no Process selected',
                additionalInfo:
                  'To see a Diagram, select a Process in the Filters panel',
              }
        }
      >
        {xml !== null && (
          <Diagram
            xml={xml}
            {...(batchModificationStore.state.isEnabled
              ? // Props for batch modification mode
                {
                  // Source and target flow node
                  selectedFlowNodeIds: [
                    ...(flowNodeId ? [flowNodeId] : []),
                    ...(selectedTargetFlowNodeId
                      ? [selectedTargetFlowNodeId]
                      : []),
                  ],
                  onFlowNodeSelection: (flowNodeId) => {
                    return batchModificationStore.selectTargetFlowNode(
                      flowNodeId ?? null,
                    );
                  },
                  overlaysData: [
                    ...(overlayData ?? []),
                    ...(batchOverlayData ?? []),
                  ],
                  // All flow nodes that can be a move modification target,
                  // except the source flow node
                  selectableFlowNodes: processXmlStore.selectableIds.filter(
                    (selectedFlowNodeId) =>
                      selectedFlowNodeId !== flowNodeId &&
                      selectedFlowNodeId !== undefined &&
                      isMoveModificationTarget(
                        processXmlStore.getFlowNode(selectedFlowNodeId),
                      ),
                  ),
                }
              : // Props for regular mode
                {
                  selectedFlowNodeIds: flowNodeId ? [flowNodeId] : undefined,
                  onFlowNodeSelection: (flowNodeId) => {
                    if (flowNodeId === null || flowNodeId === undefined) {
                      navigate(deleteSearchParams(location, ['flowNodeId']));
                    } else {
                      navigate(
                        setSearchParam(location, ['flowNodeId', flowNodeId]),
                      );
                    }
                  },
                  overlaysData: overlayData,
                  selectableFlowNodes: processXmlStore.selectableIds,
                })}
          >
            {statisticsOverlays?.map((overlay) => {
              const payload = overlay.payload as {
                flowNodeState: FlowNodeState;
                count: number;
              };

              return (
                <StateOverlay
                  testId={`state-overlay-${overlay.flowNodeId}-${payload.flowNodeState}`}
                  key={`${overlay.flowNodeId}-${payload.flowNodeState}`}
                  state={payload.flowNodeState}
                  count={payload.count}
                  container={overlay.container}
                />
              );
            })}
            {batchModificationBadgeOverlays?.map((overlay) => {
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
      {batchModificationStore.state.isEnabled && (
        <BatchModificationNotification
          sourceFlowNodeId={flowNodeId}
          targetFlowNodeId={selectedTargetFlowNodeId || undefined}
          onUndoClick={() => batchModificationStore.selectTargetFlowNode(null)}
        />
      )}
    </Section>
  );
});

export {DiagramPanel};
