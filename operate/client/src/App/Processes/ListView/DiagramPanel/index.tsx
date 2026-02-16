/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {useSearchParams} from 'react-router-dom';
import {observer} from 'mobx-react';
import {Section} from './styled';
import {DiagramShell} from 'modules/components/DiagramShell';
import {Diagram} from 'modules/components/Diagram';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {notificationsStore} from 'modules/stores/notifications';
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
import {getSelectedProcessInstancesFilter} from 'modules/queries/processInstancesStatistics/filters';

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
    status: definitionSelectionStatus,
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

  useEffect(() => {
    if (
      definitionSelectionStatus === 'success' &&
      definitionSelection.kind === 'no-match'
    ) {
      setSearchParams((p) => {
        p.delete('process');
        p.delete('version');
        return p;
      });
      notificationsStore.displayNotification({
        kind: 'error',
        title: 'Process could not be found',
        isDismissable: true,
      });
    }
  }, [definitionSelection, definitionSelectionStatus, setSearchParams]);

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
  const processInstanceKeyFilter = getSelectedProcessInstancesFilter();
  const {data: batchOverlayData} = useBatchModificationOverlayData(
    {
      filter: {
        processInstanceKey: processInstanceKeyFilter,
      },
    },
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

  const getSelectedFlowNodeIds = () => {
    if (!batchModificationStore.state.isEnabled) {
      return flowNodeId ? [flowNodeId] : undefined;
    }

    const ids: string[] = [];
    if (flowNodeId) {
      ids.push(flowNodeId);
    }
    if (selectedTargetElementId) {
      ids.push(selectedTargetElementId);
    }
    return ids;
  };

  const getSelectableFlowNodes = () => {
    if (!batchModificationStore.state.isEnabled) {
      return selectableIds;
    }

    return selectableIds?.filter((selectedFlowNodeId) => {
      if (selectedFlowNodeId === flowNodeId) {
        return false;
      }
      if (selectedFlowNodeId === undefined) {
        return false;
      }

      const flowNode = getFlowNode({
        businessObjects: processDefinitionXML?.diagramModel.elementsById,
        flowNodeId: selectedFlowNodeId,
      });

      return isMoveModificationTarget(flowNode);
    });
  };

  const getOverlaysData = () => {
    const baseOverlays = processInstanceOverlayData ?? [];

    if (batchModificationStore.state.isEnabled) {
      return [...baseOverlays, ...(batchOverlayData ?? [])];
    }

    return [
      ...baseOverlays,
      ...((subprocessOverlays ?? []) as typeof baseOverlays),
    ];
  };

  const handleFlowNodeSelection = (
    selectedFlowNodeId: string | null | undefined,
  ) => {
    if (batchModificationStore.state.isEnabled) {
      return batchModificationStore.selectTargetElement(
        selectedFlowNodeId ?? null,
      );
    }

    if (selectedFlowNodeId === null || selectedFlowNodeId === undefined) {
      setSearchParams((p) => {
        p.delete('flowNodeId');
        return p;
      });
      return;
    }

    setSearchParams((p) => {
      p.set('flowNodeId', selectedFlowNodeId);
      return p;
    });
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
            selectedFlowNodeIds={getSelectedFlowNodeIds()}
            onFlowNodeSelection={handleFlowNodeSelection}
            overlaysData={getOverlaysData()}
            selectableFlowNodes={getSelectableFlowNodes()}
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
