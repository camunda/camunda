/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useSearchParams} from 'react-router-dom';
import {observer} from 'mobx-react';
import {Section} from '../../DiagramPanel/styled';
import {DiagramShell} from 'modules/components/DiagramShell';
import {Diagram} from 'modules/components/Diagram';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {StateOverlay} from 'modules/components/StateOverlay';
import {batchModificationStore} from 'modules/stores/batchModification';
import {isMoveModificationTarget} from 'modules/bpmn-js/utils/isMoveModificationTarget';
import {ModificationBadgeOverlay} from 'App/ProcessInstance/TopPanel/ModificationBadgeOverlay';
import {BatchModificationNotification} from './BatchModificationNotification';
import {DiagramHeader} from './DiagramHeader';
import {useProcessInstancesOverlayData} from 'modules/queries/processInstancesStatistics/useOverlayData';
import {useBatchModificationOverlayData} from 'modules/queries/processInstancesStatistics/useBatchModificationOverlayData';
import {useListViewXml} from 'modules/queries/processDefinitions/useListViewXml';
import {
  getFlowNode,
  getSubprocessOverlayFromIncidentFlowNodes,
} from 'modules/utils/flowNodes';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';
import type {FlowNodeState} from 'modules/types/operate';
import {parseProcessInstancesFilter} from 'modules/utils/filter/v2/processInstancesSearch';
import {
  getProcessDefinitionName,
  useProcessDefinitionSelection,
} from 'modules/hooks/processDefinitions';

const OVERLAY_TYPE_BATCH_MODIFICATIONS_BADGE = 'batchModificationsBadge';

type ModificationBadgePayload = {
  newTokenCount: number;
  cancelledTokenCount: number;
};

const DiagramPanel: React.FC = observer(() => {
  const [searchParams, setSearchParams] = useSearchParams();
  const {flowNodeId} = parseProcessInstancesFilter(searchParams);

  const {
    data: definitionSelection = {kind: 'no-match'},
    isLoading: isDefinitionSelectionLoading,
    isEnabled: isDefinitionSelectionEnabled,
    isError: isDefinitionSelectionError,
  } = useProcessDefinitionSelection();
  const selectedDefinitionKey =
    definitionSelection.kind === 'single-version'
      ? definitionSelection.definition.processDefinitionKey
      : undefined;
  const selectedDefinitionName =
    definitionSelection.kind !== 'no-match'
      ? getProcessDefinitionName(definitionSelection.definition)
      : 'Process';

  const statisticsOverlays = diagramOverlaysStore.state.overlays.filter(
    ({type}) => type.match(/^statistics/) !== null,
  );

  const batchModificationBadgeOverlays =
    diagramOverlaysStore.state.overlays.filter(
      ({type}) => type === OVERLAY_TYPE_BATCH_MODIFICATIONS_BADGE,
    );

  const {
    data: processDefinitionXML,
    isFetching: isXmlFetching,
    isError: isXmlError,
  } = useListViewXml({
    processDefinitionKey: selectedDefinitionKey,
  });
  const selectableIds = processDefinitionXML?.selectableFlowNodes.map(
    (flowNode) => flowNode.id,
  );

  const {data: businessObjects} = useBusinessObjects();

  const {data: processInstanceOverlayData} = useProcessInstancesOverlayData(
    {},
    selectedDefinitionKey,
  );

  const {selectedTargetElementId} = batchModificationStore.state;
  const {data: batchOverlayData} = useBatchModificationOverlayData(
    {},
    {
      sourceFlowNodeId: flowNodeId,
      targetFlowNodeId: selectedTargetElementId ?? undefined,
    },
    selectedDefinitionKey,
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

  const getStatus = () => {
    switch (true) {
      case isXmlFetching || isDefinitionSelectionLoading:
        return 'loading';
      case isXmlError || isDefinitionSelectionError:
        return 'error';
      case !isDefinitionSelectionEnabled ||
        definitionSelection.kind !== 'single-version':
        return 'empty';
      default:
        return 'content';
    }
  };

  return (
    <Section aria-label="Diagram Panel">
      <DiagramHeader processDefinitionSelection={definitionSelection} />
      <DiagramShell
        status={getStatus()}
        emptyMessage={
          definitionSelection.kind === 'all-versions'
            ? {
                message: `There is more than one Version selected for Process "${selectedDefinitionName}"`,
                additionalInfo: 'To see a Diagram, select a single Version',
              }
            : {
                message: 'There is no Process selected',
                additionalInfo:
                  'To see a Diagram, select a Process in the Filters panel',
              }
        }
      >
        {processDefinitionXML?.xml !== undefined && (
          <Diagram
            xml={processDefinitionXML.xml}
            processDefinitionKey={selectedDefinitionKey}
            {...(batchModificationStore.state.isEnabled
              ? // Props for batch modification mode
                {
                  // Source and target flow node
                  selectedFlowNodeIds: [
                    ...(flowNodeId ? [flowNodeId] : []),
                    ...(selectedTargetElementId
                      ? [selectedTargetElementId]
                      : []),
                  ],
                  onFlowNodeSelection: (flowNodeId) => {
                    return batchModificationStore.selectTargetElement(
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
                            processDefinitionXML?.diagramModel.elementsById,
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
                      setSearchParams((p) => {
                        p.delete('flowNodeId');
                        return p;
                      });
                    } else {
                      setSearchParams((p) => {
                        p.set('flowNodeId', flowNodeId);
                        return p;
                      });
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
          targetFlowNodeId={selectedTargetElementId || undefined}
          onUndoClick={() => batchModificationStore.selectTargetElement(null)}
        />
      )}
    </Section>
  );
});

export {DiagramPanel};
