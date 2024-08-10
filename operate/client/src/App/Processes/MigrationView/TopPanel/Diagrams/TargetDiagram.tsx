/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {Header} from './Header';
import {DiagramWrapper} from './styled';
import {observer} from 'mobx-react';
import {TargetProcessField} from './TargetProcessField';
import {TargetVersionField} from './TargetVersionField';
import {processesStore} from 'modules/stores/processes/processes.migration';
import {processXmlStore} from 'modules/stores/processXml/processXml.migration.target';

import {DiagramShell} from 'modules/components/DiagramShell';
import {Diagram} from 'modules/components/Diagram';
import {useEffect} from 'react';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {ModificationBadgeOverlay} from 'App/ProcessInstance/TopPanel/ModificationBadgeOverlay';

const OVERLAY_TYPE = 'migrationTargetSummary';

const TargetDiagram: React.FC = observer(() => {
  const {
    migrationState: {selectedTargetVersion},
    selectedTargetProcessId,
  } = processesStore;
  const isDiagramLoading = processXmlStore.state.status === 'fetching';
  const isVersionSelected = selectedTargetVersion !== null;
  const {isSummaryStep} = processInstanceMigrationStore;
  const stateOverlays = diagramOverlaysStore.state.overlays.filter(
    ({type}) => type === OVERLAY_TYPE,
  );

  useEffect(() => {
    if (selectedTargetProcessId !== undefined) {
      processXmlStore.fetchProcessXml(selectedTargetProcessId);
    } else {
      processXmlStore.reset();
    }
    processInstanceMigrationStore.setTargetProcessDefinitionKey(
      selectedTargetProcessId ?? null,
    );
  }, [selectedTargetProcessId]);

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
    <DiagramWrapper>
      {!processInstanceMigrationStore.isSummaryStep && (
        <Header mode="edit" label="Target">
          <TargetProcessField />
          <TargetVersionField />
        </Header>
      )}
      <DiagramShell
        status={getStatus()}
        emptyMessage={{
          message: 'Select a target process and version',
        }}
        messagePosition="center"
      >
        {processXmlStore.state.xml !== null && (
          <Diagram
            xml={processXmlStore.state.xml}
            selectableFlowNodes={processXmlStore.selectableIds}
            selectedFlowNodeIds={
              processInstanceMigrationStore.selectedTargetFlowNodeId
                ? [processInstanceMigrationStore.selectedTargetFlowNodeId]
                : undefined
            }
            onFlowNodeSelection={
              processInstanceMigrationStore.selectTargetFlowNode
            }
            overlaysData={
              isSummaryStep
                ? Object.entries(
                    processInstanceMigrationStore.flowNodeCountByTargetId,
                  ).map(([targetId, count]) => ({
                    payload: {count},
                    type: OVERLAY_TYPE,
                    flowNodeId: targetId,
                    position: {top: -14, right: -7},
                  }))
                : []
            }
          >
            {isSummaryStep &&
              stateOverlays.map((overlay) => {
                const payload = overlay.payload as {
                  count: number;
                };
                return (
                  <ModificationBadgeOverlay
                    key={overlay.flowNodeId}
                    newTokenCount={payload.count}
                    cancelledTokenCount={0}
                    container={overlay.container}
                  />
                );
              })}
          </Diagram>
        )}
      </DiagramShell>
    </DiagramWrapper>
  );
});

export {TargetDiagram};
