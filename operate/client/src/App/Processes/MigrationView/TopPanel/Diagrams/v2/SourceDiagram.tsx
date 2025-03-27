/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {processesStore} from 'modules/stores/processes/processes.migration';
import {processXmlStore} from 'modules/stores/processXml/processXml.migration.source';
import {Header} from '../Header';
import {DiagramWrapper} from '../styled';
import {observer} from 'mobx-react';
import {DiagramShell} from 'modules/components/DiagramShell';
import {Diagram} from 'modules/components/Diagram';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {StateOverlay} from 'modules/components/StateOverlay';
import {useProcessInstancesOverlayData} from 'modules/queries/processInstancesStatistics/useOverlayData';
import {useProcessInstanceFilters} from 'modules/hooks/useProcessInstancesFilters';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {getProcessInstanceKey} from 'modules/utils/statistics/processInstances';

const SourceDiagram: React.FC = observer(() => {
  const {processName, version} = processesStore.getSelectedProcessDetails();
  const {selectedSourceFlowNodeIds} = processInstanceMigrationStore;

  const statisticsOverlays = diagramOverlaysStore.state.overlays.filter(
    ({type}) => type.match(/^statistics/) !== null,
  );

  const processInstanceFilters = useProcessInstanceFilters();

  const {data: overlayData} = useProcessInstancesOverlayData(
    {
      ...processInstanceFilters,
      processInstanceKey: getProcessInstanceKey(),
    },
    processInstancesSelectionStore.selectedProcessInstanceIds.length > 0,
  );

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
      <DiagramShell status="content">
        {processXmlStore.state.xml !== null && (
          <Diagram
            xml={processXmlStore.state.xml}
            selectableFlowNodes={processXmlStore.selectableIds}
            selectedFlowNodeIds={selectedSourceFlowNodeIds}
            onFlowNodeSelection={(flowNodeId) => {
              processInstanceMigrationStore.selectSourceFlowNode(flowNodeId);
            }}
            overlaysData={
              processInstanceMigrationStore.isSummaryStep ? overlayData : []
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
