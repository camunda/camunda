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
import {DiagramShell} from 'modules/components/DiagramShell';
import {Diagram} from 'modules/components/Diagram';
import {useEffect} from 'react';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {ModificationBadgeOverlay} from 'App/ProcessInstance/TopPanel/ModificationBadgeOverlay';
import {useMigrationTargetXml} from 'modules/queries/processDefinitions/useMigrationTargetXml';

const OVERLAY_TYPE = 'migrationTargetSummary';

const TargetDiagram: React.FC = observer(() => {
  const {
    migrationState: {selectedTargetVersion},
    selectedTargetProcessId,
  } = processesStore;
  const isVersionSelected = selectedTargetVersion !== null;
  const {isSummaryStep} = processInstanceMigrationStore;
  const stateOverlays = diagramOverlaysStore.state.overlays.filter(
    ({type}) => type === OVERLAY_TYPE,
  );

  const {
    data,
    isLoading: isMigrationTargetXmlLoading,
    isError: isMigrationTargetXmlError,
  } = useMigrationTargetXml({
    processDefinitionKey: selectedTargetProcessId!,
    enabled: selectedTargetProcessId !== undefined,
  });

  useEffect(() => {
    processInstanceMigrationStore.setTargetProcessDefinitionKey(
      selectedTargetProcessId ?? null,
    );
  }, [selectedTargetProcessId]);

  const getStatus = () => {
    if (isMigrationTargetXmlLoading) {
      return 'loading';
    }
    if (isMigrationTargetXmlError) {
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
        {data?.xml !== undefined && (
          <Diagram
            xml={data.xml}
            selectableFlowNodes={data.selectableFlowNodes.map(
              (flowNode) => flowNode.id,
            )}
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
