/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {Header} from '../Header';
import {DiagramWrapper} from '../styled';
import {observer} from 'mobx-react';
import {TargetProcessField} from '../TargetProcessField';
import {TargetVersionField} from '../TargetVersionField';
import {DiagramShell} from 'modules/components/DiagramShell';
import {Diagram} from 'modules/components/Diagram';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {ModificationBadgeOverlay} from 'App/ProcessInstance/TopPanel/ModificationBadgeOverlay';
import {useProcessInstancesElementStates} from 'modules/queries/processInstancesStatistics/useElementStates';
import {useMigrationTargetXml} from 'modules/queries/processDefinitions/useMigrationTargetXml';
import {getMigrationProcessInstancesFilter} from 'modules/queries/processInstancesStatistics/filters';

const OVERLAY_TYPE = 'migrationTargetSummary';

const TargetDiagram: React.FC = observer(() => {
  const {
    isSummaryStep,
    state: {sourceProcessDefinition, targetProcessDefinition},
  } = processInstanceMigrationStore;
  const stateOverlays = diagramOverlaysStore.state.overlays.filter(
    ({type}) => type === OVERLAY_TYPE,
  );

  const {
    data,
    isLoading: isMigrationTargetXmlLoading,
    isError: isMigrationTargetXmlError,
  } = useMigrationTargetXml({
    processDefinitionKey: targetProcessDefinition?.processDefinitionKey,
    processDefinitionId: targetProcessDefinition?.processDefinitionId,
  });

  const {data: elementData} = useProcessInstancesElementStates(
    {
      filter: {
        processInstanceKey: getMigrationProcessInstancesFilter(),
      },
    },
    sourceProcessDefinition?.processDefinitionKey,
    processInstanceMigrationStore.state.selectedInstancesCount > 0,
  );

  const getStatus = () => {
    if (isMigrationTargetXmlLoading) {
      return 'loading';
    }
    if (isMigrationTargetXmlError) {
      return 'error';
    }
    if (!targetProcessDefinition) {
      return 'empty';
    }
    return 'content';
  };

  const getElementCountByTargetId = () => {
    return Object.entries(
      processInstanceMigrationStore.state.elementMapping,
    ).reduce<{
      [targetElementId: string]: number;
    }>((mappingByTarget, [sourceElementId, targetElementId]) => {
      const previousCount = mappingByTarget[targetElementId];
      const newCount = elementData
        ? elementData
            .filter((state) => {
              return (
                state.elementId === sourceElementId &&
                ['active', 'incidents'].includes(state.elementState)
              );
            })
            .reduce((count, state) => {
              return count + state.count;
            }, 0)
        : 0;

      return {
        ...mappingByTarget,
        [targetElementId]:
          previousCount !== undefined ? previousCount + newCount : newCount,
      };
    }, {});
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
            processDefinitionKey={targetProcessDefinition?.processDefinitionKey}
            selectableElements={[
              ...data.selectableElements,
              ...data.selectableSequenceFlows,
            ].map((element) => element.id)}
            selectedElementIds={
              processInstanceMigrationStore.selectedTargetElementId
                ? [processInstanceMigrationStore.selectedTargetElementId]
                : undefined
            }
            onElementSelection={
              processInstanceMigrationStore.selectTargetElement
            }
            overlaysData={
              isSummaryStep
                ? Object.entries(getElementCountByTargetId()).map(
                    ([targetId, count]) => ({
                      payload: {count},
                      type: OVERLAY_TYPE,
                      elementId: targetId,
                      position: {top: -14, right: -7},
                    }),
                  )
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
                    key={overlay.elementId}
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
