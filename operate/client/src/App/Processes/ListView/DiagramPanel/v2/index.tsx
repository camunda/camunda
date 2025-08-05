/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useRef} from 'react';
import {useLocation, useNavigate, type Location} from 'react-router-dom';
import {observer} from 'mobx-react';
import {useOperationsPanelResize} from 'modules/hooks/useOperationsPanelResize';
import {deleteSearchParams} from 'modules/utils/filter';
import {getProcessInstanceFilters} from 'modules/utils/filter/getProcessInstanceFilters';
import {COLLAPSABLE_PANEL_MIN_WIDTH} from 'modules/constants';
import {processesStore} from 'modules/stores/processes/processes.list';
import {Section} from '../styled';
import {DiagramShell} from 'modules/components/DiagramShell';
import {Diagram} from 'modules/components/Diagram/v2';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {StateOverlay} from 'modules/components/StateOverlay';
import {batchModificationStore} from 'modules/stores/batchModification';
import {isMoveModificationTarget} from 'modules/bpmn-js/utils/isMoveModificationTarget';
import {ModificationBadgeOverlay} from 'App/ProcessInstance/TopPanel/ModificationBadgeOverlay';
import {BatchModificationNotification} from '../BatchModificationNotification/v2';
import {DiagramHeader} from '../DiagramHeader';
import {useProcessInstancesOverlayData} from 'modules/queries/processInstancesStatistics/useOverlayData';
import {useBatchModificationOverlayData} from 'modules/queries/processInstancesStatistics/useBatchModificationOverlayData';
import {useProcessDefinitionKeyContext} from '../../processDefinitionKeyContext';
import {useListViewXml} from 'modules/queries/processDefinitions/useListViewXml';
import {
  getFlowNode,
  getSubprocessOverlayFromIncidentFlowNodes,
} from 'modules/utils/flowNodes';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';
import type {FlowNodeState} from 'modules/types/operate';

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
  const {version, flowNodeId, tenant} = getProcessInstanceFilters(
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

  const processId = processesStore.getProcessIdByLocation(location);

  const {selectedTargetItemId} = batchModificationStore.state;

  const processDefinitionKey = useProcessDefinitionKeyContext();
  const processDefinition = useListViewXml({
    processDefinitionKey,
  });
  const xml = processDefinition?.data?.xml;
  const selectableIds = processDefinition?.data?.selectableFlowNodes.map(
    (flowNode) => flowNode.id,
  );

  const panelHeaderRef = useRef<HTMLDivElement>(null);

  useOperationsPanelResize(panelHeaderRef, (target, width) => {
    target.style['marginRight'] =
      `calc(${width}px - ${COLLAPSABLE_PANEL_MIN_WIDTH})`;
  });

  const {data: businessObjects} = useBusinessObjects();

  const {data: processInstanceOverlayData} = useProcessInstancesOverlayData(
    {},
    processId,
  );

  const {data: batchOverlayData} = useBatchModificationOverlayData(
    {},
    {
      sourceFlowNodeId: flowNodeId,
      targetFlowNodeId: selectedTargetItemId ?? undefined,
    },
    processId,
    batchModificationStore.state.isEnabled,
  );

  const flowNodeIdsWithIncidents = processInstanceOverlayData
    ?.filter(({type}) => type === 'statistics-incidents')
    ?.map((overlay) => overlay.flowNodeId);

  const selectableFlowNodesWithIncidents = flowNodeIdsWithIncidents?.map(
    (flowNodeId) => businessObjects?.[flowNodeId],
  );

  const subprocessOverlays = getSubprocessOverlayFromIncidentFlowNodes(
    selectableFlowNodesWithIncidents,
    'statistics-incidents',
  );

  const isDiagramLoading =
    processDefinition?.isFetching ||
    !processesStore.isInitialLoadComplete ||
    (processesStore.state.status === 'fetching' &&
      location.state?.refreshContent);

  const getStatus = () => {
    if (isDiagramLoading) {
      return 'loading';
    }
    if (processDefinition?.isError) {
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
        tenant={tenant}
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
        {xml !== undefined && (
          <Diagram
            xml={xml}
            {...(batchModificationStore.state.isEnabled
              ? // Props for batch modification mode
                {
                  // Source and target flow node
                  selectedFlowNodeIds: [
                    ...(flowNodeId ? [flowNodeId] : []),
                    ...(selectedTargetItemId ? [selectedTargetItemId] : []),
                  ],
                  onFlowNodeSelection: (flowNodeId) => {
                    return batchModificationStore.selectTargetItem(
                      flowNodeId ?? null,
                    );
                  },
                  overlaysData: [
                    ...(processInstanceOverlayData ?? []),
                    ...(batchOverlayData ?? []),
                  ],
                  // All flow nodes that can be a move modification target,
                  // except the source flow node
                  selectableFlowNodes: selectableIds?.filter(
                    (selectedFlowNodeId) =>
                      selectedFlowNodeId !== flowNodeId &&
                      selectedFlowNodeId !== undefined &&
                      isMoveModificationTarget(
                        getFlowNode({
                          businessObjects:
                            processDefinition.data?.diagramModel.elementsById,
                          flowNodeId: selectedFlowNodeId,
                        }),
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
                  overlaysData: [
                    ...(processInstanceOverlayData ?? []),
                    ...(subprocessOverlays ?? []),
                  ],
                  selectableFlowNodes: selectableIds,
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
          targetFlowNodeId={selectedTargetItemId || undefined}
          onUndoClick={() => batchModificationStore.selectTargetItem(null)}
        />
      )}
    </Section>
  );
});

export {DiagramPanel};
