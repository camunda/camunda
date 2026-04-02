/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Header} from '../Header';
import {DiagramWrapper} from '../styled';
import {observer} from 'mobx-react';
import {DiagramShell} from 'modules/components/DiagramShell';
import {Diagram} from 'modules/components/Diagram';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {StateOverlay} from 'modules/components/StateOverlay';
import {useProcessInstancesOverlayData} from 'modules/queries/processInstancesStatistics/useOverlayData';
import {getMigrationProcessInstancesFilter} from 'modules/queries/processInstancesStatistics/filters';
import {useMigrationSourceXml} from 'modules/queries/processDefinitions/useMigrationSourceXml';
import type {ElementState} from 'modules/bpmn-js/overlayTypes';
import {getProcessDefinitionName} from 'modules/hooks/processDefinitions';

const SourceDiagram: React.FC = observer(() => {
  const {
    selectedSourceElementIds,
    state: {sourceProcessDefinition},
  } = processInstanceMigrationStore;

  const processVersion = sourceProcessDefinition?.version?.toString() ?? '';
  const processName = sourceProcessDefinition
    ? getProcessDefinitionName(sourceProcessDefinition)
    : 'Process';

  const statisticsOverlays = diagramOverlaysStore.state.overlays.filter(
    ({type}) => type.match(/^statistics/) !== null,
  );

  const {
    data: migrationSourceData,
    isLoading: isMigrationSourceXmlLoading,
    isError: isMigrationSourceXmlError,
  } = useMigrationSourceXml({
    processDefinitionKey: sourceProcessDefinition?.processDefinitionKey,
    processDefinitionId: sourceProcessDefinition?.processDefinitionId,
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

  const {data: overlayData} = useProcessInstancesOverlayData(
    {
      filter: {
        processInstanceKey: getMigrationProcessInstancesFilter(),
      },
    },
    sourceProcessDefinition?.processDefinitionKey,
    processInstanceMigrationStore.state.selectedInstancesCount > 0,
  );

  return (
    <DiagramWrapper>
      {!processInstanceMigrationStore.isSummaryStep && (
        <Header
          mode="view"
          label="Source"
          processName={processName}
          processVersion={processVersion}
        />
      )}
      <DiagramShell status={getStatus()}>
        {migrationSourceData?.xml !== undefined && (
          <Diagram
            xml={migrationSourceData.xml}
            processDefinitionKey={sourceProcessDefinition?.processDefinitionKey}
            selectableElements={[
              ...migrationSourceData.selectableElements,
              ...migrationSourceData.selectableSequenceFlows,
            ].map((element) => element.id)}
            selectedElementIds={selectedSourceElementIds}
            onElementSelection={(elementId) => {
              processInstanceMigrationStore.selectSourceElement(elementId);
            }}
            overlaysData={
              processInstanceMigrationStore.isSummaryStep ? overlayData : []
            }
          >
            {processInstanceMigrationStore.isSummaryStep &&
              statisticsOverlays.map((overlay) => {
                const payload = overlay.payload as {
                  elementState: ElementState;
                  count: number;
                };

                return (
                  <StateOverlay
                    key={`${overlay.elementId}-${payload.elementState}`}
                    state={payload.elementState}
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
