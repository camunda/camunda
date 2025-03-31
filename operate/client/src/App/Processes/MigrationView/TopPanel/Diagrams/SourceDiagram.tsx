/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {processesStore} from 'modules/stores/processes/processes.migration';
import {Header} from './Header';
import {DiagramWrapper} from './styled';
import {observer} from 'mobx-react';
import {DiagramShell} from 'modules/components/DiagramShell';
import {Diagram} from 'modules/components/Diagram';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {processStatisticsStore} from 'modules/stores/processStatistics/processStatistics.migration.source';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {StateOverlay} from 'modules/components/StateOverlay';
import {useMigrationSourceXml} from 'modules/queries/processDefinitions/useMigrationSourceXml';

const SourceDiagram: React.FC = observer(() => {
  const {processName, version, bpmnProcessId} =
    processesStore.getSelectedProcessDetails();
  const {
    selectedSourceFlowNodeIds,
    state: {sourceProcessDefinitionKey},
  } = processInstanceMigrationStore;

  const statisticsOverlays = diagramOverlaysStore.state.overlays.filter(
    ({type}) => type.match(/^statistics/) !== null,
  );

  const {
    data,
    isLoading: isMigrationSourceXmlLoading,
    isError: isMigrationSourceXmlError,
  } = useMigrationSourceXml({
    processDefinitionKey: sourceProcessDefinitionKey ?? undefined,
    bpmnProcessId,
  });

  const getStatus = () => {
    if (isMigrationSourceXmlLoading) {
      return 'loading';
    }
    if (isMigrationSourceXmlError) {
      return 'error';
    }
    return 'content';
  };

  return (
    <DiagramWrapper>
      {!processInstanceMigrationStore.isSummaryStep && (
        <Header
          mode="view"
          label="Source"
          processName={processName}
          processVersion={version ?? ''}
        />
      )}
      <DiagramShell status={getStatus()}>
        {data?.xml !== undefined && (
          <Diagram
            xml={data.xml}
            selectableFlowNodes={data.selectableFlowNodes.map(
              (flowNode) => flowNode.id,
            )}
            selectedFlowNodeIds={selectedSourceFlowNodeIds}
            onFlowNodeSelection={(flowNodeId) => {
              processInstanceMigrationStore.selectSourceFlowNode(flowNodeId);
            }}
            overlaysData={
              processInstanceMigrationStore.isSummaryStep
                ? processStatisticsStore.overlaysData
                : []
            }
          >
            {processInstanceMigrationStore.isSummaryStep &&
              statisticsOverlays.map((overlay) => {
                const payload = overlay.payload as {
                  flowNodeState: FlowNodeState;
                  count: number;
                };

                return (
                  <StateOverlay
                    key={`${overlay.flowNodeId}-${payload.flowNodeState}`}
                    state={payload.flowNodeState}
                    count={payload.count}
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

export {SourceDiagram};
