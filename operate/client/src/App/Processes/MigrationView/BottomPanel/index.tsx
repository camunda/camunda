/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {observer} from 'mobx-react';
import {Select, SelectItem} from '@carbon/react';
import {ArrowRight} from '@carbon/react/icons';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {processXmlStore as processXmlMigrationSourceStore} from 'modules/stores/processXml/processXml.migration.source';
import {processXmlStore as processXmlMigrationTargetStore} from 'modules/stores/processXml/processXml.migration.target';
import {autoMappingStore} from 'modules/stores/autoMapping';
import {ErrorMessage} from 'modules/components/ErrorMessage';
import {
  BottomSection,
  DataTable,
  ErrorMessageContainer,
  LeftColumn,
} from './styled';

const BottomPanel: React.FC = observer(() => {
  const {selectedSourceFlowNodeIds} = processInstanceMigrationStore;

  const handleCheckIsRowSelected = (selectedSourceFlowNodes?: string[]) => {
    return (rowId: string) => selectedSourceFlowNodes?.includes(rowId) ?? false;
  };
  const {
    updateFlowNodeMapping,
    clearFlowNodeMapping,
    state: {flowNodeMapping},
  } = processInstanceMigrationStore;

  const {autoMappableFlowNodes, isAutoMappable} = autoMappingStore;

  // Automatically map flow nodes with same id and type in source and target diagrams
  useEffect(() => {
    clearFlowNodeMapping();
    autoMappableFlowNodes.forEach((sourceFlowNode) => {
      updateFlowNodeMapping({
        sourceId: sourceFlowNode.id,
        targetId: sourceFlowNode.id,
      });
    });
  }, [autoMappableFlowNodes, updateFlowNodeMapping, clearFlowNodeMapping]);

  return (
    <BottomSection>
      {!processXmlMigrationSourceStore.hasSelectableFlowNodes ? (
        <ErrorMessageContainer>
          <ErrorMessage
            message="There are no mappable flow nodes"
            additionalInfo="Exit migration to select a different process"
          />
        </ErrorMessageContainer>
      ) : (
        <DataTable
          size="md"
          headers={[
            {
              header: 'Source flow nodes',
              key: 'sourceFlowNode',
              width: '50%',
            },
            {
              header: 'Target flow nodes',
              key: 'targetFlowNode',
              width: '50%',
            },
          ]}
          onRowClick={(rowId) => {
            processInstanceMigrationStore.selectSourceFlowNode(rowId);
          }}
          checkIsRowSelected={handleCheckIsRowSelected(
            selectedSourceFlowNodeIds,
          )}
          rows={processXmlMigrationSourceStore.selectableFlowNodes.map(
            (sourceFlowNode) => {
              const selectableFlowNodes =
                processXmlMigrationTargetStore.selectableFlowNodes.filter(
                  (flowNode) => {
                    return sourceFlowNode.$type === flowNode.$type;
                  },
                );
              return {
                id: sourceFlowNode.id,
                sourceFlowNode: (
                  <LeftColumn>
                    <div>{sourceFlowNode.name}</div>
                    <ArrowRight />
                  </LeftColumn>
                ),
                targetFlowNode: (() => {
                  const targetFlowNodeId =
                    flowNodeMapping[sourceFlowNode.id] ?? '';

                  return (
                    <Select
                      disabled={
                        processInstanceMigrationStore.state.currentStep ===
                          'summary' || selectableFlowNodes.length === 0
                      }
                      size="sm"
                      hideLabel
                      labelText={`Target flow node for ${sourceFlowNode.name}`}
                      id={sourceFlowNode.id}
                      value={targetFlowNodeId}
                      onChange={({target}) => {
                        processInstanceMigrationStore.updateFlowNodeMapping({
                          sourceId: sourceFlowNode.id,
                          targetId: target.value,
                        });
                      }}
                    >
                      {[{id: '', name: ''}, ...selectableFlowNodes].map(
                        ({id, name}) => {
                          return <SelectItem key={id} value={id} text={name} />;
                        },
                      )}
                    </Select>
                  );
                })(),
              };
            },
          )}
        />
      )}
    </BottomSection>
  );
});

export {BottomPanel};
