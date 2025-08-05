/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo} from 'react';
import {observer} from 'mobx-react';
import {SelectItem, Stack, Tag, Toggle} from '@carbon/react';

import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {processInstanceMigrationMappingStore} from 'modules/stores/processInstanceMigrationMapping';
import {ErrorMessage} from 'modules/components/ErrorMessage';
import {
  BottomSection,
  CheckmarkFilled,
  DataTable,
  ErrorMessageContainer,
  LeftColumn,
  IconContainer,
  Select,
  SourceItemName,
  ArrowRight,
  ToggleContainer,
} from './styled';
import {useMigrationSourceXml} from 'modules/queries/processDefinitions/useMigrationSourceXml';
import {useMigrationTargetXml} from 'modules/queries/processDefinitions/useMigrationTargetXml';
import {processesStore} from 'modules/stores/processes/processes.migration';

const TOGGLE_LABEL = 'Show only not mapped';

const BottomPanel: React.FC = observer(() => {
  const {selectedSourceItemIds} = processInstanceMigrationStore;

  const handleCheckIsRowSelected = (selectedSourceFlowNodes?: string[]) => {
    return (rowId: string) => selectedSourceFlowNodes?.includes(rowId) ?? false;
  };
  const {
    updateItemMapping,
    clearItemMapping,
    state: {itemMapping, sourceProcessDefinitionKey},
  } = processInstanceMigrationStore;

  const {
    toggleMappedFilter,
    state: {isMappedFilterEnabled},
    getMappableFlowNodes,
    getMappableSequenceFlows,
  } = processInstanceMigrationMappingStore;

  const {selectedTargetProcessId} = processesStore;
  const isTargetSelected = !!selectedTargetProcessId;

  const {data: sourceData} = useMigrationSourceXml({
    processDefinitionKey: sourceProcessDefinitionKey ?? undefined,
    bpmnProcessId: processesStore.getSelectedProcessDetails().bpmnProcessId,
  });

  const {data: targetData} = useMigrationTargetXml({
    processDefinitionKey: selectedTargetProcessId,
  });

  const mappableFlowNodes = getMappableFlowNodes(
    sourceData?.selectableFlowNodes,
    targetData?.selectableFlowNodes,
  );

  const mappableSequenceFlows =
    getMappableSequenceFlows(
      sourceData?.selectableSequenceFlows,
      targetData?.selectableSequenceFlows,
    ) ?? [];

  const filteredSourceItemMappings = [
    ...mappableFlowNodes,
    ...mappableSequenceFlows,
  ].filter(({sourceItem}) => {
    return !isMappedFilterEnabled || itemMapping[sourceItem.id] === undefined;
  });

  /**
   * Items (elements and sequence flows) which are contained in both source diagram and target diagram.
   *
   * An item is auto-mappable when
   * - the item id is contained in source and target diagram
   * - the bpmn type is matching in source and target diagram
   */
  const autoMappableItems = useMemo(() => {
    if (sourceData === undefined || targetData === undefined) return [];

    return [
      ...sourceData.selectableFlowNodes,
      ...sourceData.selectableSequenceFlows,
    ]
      .filter((sourceItem) => {
        const targetItem = [
          ...targetData.selectableFlowNodes,
          ...targetData.selectableSequenceFlows,
        ].find((item) => item.id === sourceItem.id);

        return (
          targetItem !== undefined && sourceItem.$type === targetItem.$type
        );
      })
      .map(({id, $type}) => {
        return {id, type: $type};
      });
  }, [sourceData, targetData]);

  /**
   * Returns true if an item with flowNodeId is auto-mappable
   */
  const isAutoMappable = (flowNodeId: string) => {
    return (
      autoMappableItems.find(({id}) => {
        return flowNodeId === id;
      }) !== undefined
    );
  };

  const hasSelectableSourceFlowNodes =
    sourceData?.selectableFlowNodes &&
    sourceData.selectableFlowNodes.length > 0;

  const hasSelectableSourceSequenceFlows =
    sourceData?.selectableSequenceFlows &&
    sourceData.selectableSequenceFlows.length > 0;

  // Automatically map items with same id and type in source and target diagrams
  useEffect(() => {
    clearItemMapping();
    autoMappableItems.forEach((sourceItem) => {
      updateItemMapping({
        sourceId: sourceItem.id,
        targetId: sourceItem.id,
      });
    });
  }, [autoMappableItems, updateItemMapping, clearItemMapping]);
  useEffect(() => {
    // reset store on unmount
    return processInstanceMigrationMappingStore.reset;
  }, []);

  return (
    <BottomSection>
      {!hasSelectableSourceFlowNodes && !hasSelectableSourceSequenceFlows ? (
        <ErrorMessageContainer>
          <ErrorMessage
            message="There are no mappable flow nodes or sequence flows."
            additionalInfo="Exit migration to select a different process"
          />
        </ErrorMessageContainer>
      ) : (
        <>
          {isTargetSelected && (
            <ToggleContainer>
              <Toggle
                size="sm"
                id="not-mapped-toggle"
                labelA={TOGGLE_LABEL}
                labelB={TOGGLE_LABEL}
                onToggle={toggleMappedFilter}
              />
              <ArrowRight />
            </ToggleContainer>
          )}
          <DataTable
            size="md"
            headers={[
              {
                header: 'Source items',
                key: 'sourceItem',
                width: '50%',
              },
              {
                header: 'Target items',
                key: 'targetItem',
                width: '50%',
              },
            ]}
            onRowClick={(rowId) => {
              processInstanceMigrationStore.selectSourceFlowNode(rowId);
            }}
            checkIsRowSelected={handleCheckIsRowSelected(selectedSourceItemIds)}
            rows={filteredSourceItemMappings.map(
              ({sourceItem, selectableTargetItem}) => {
                const isMapped = itemMapping[sourceItem.id] !== undefined;

                return {
                  id: sourceItem.id,
                  sourceItem: (
                    <LeftColumn>
                      <SourceItemName>
                        {sourceItem.name ?? sourceItem.id}
                      </SourceItemName>
                      {isTargetSelected && !isMapped && (
                        <Tag type="blue">Not mapped</Tag>
                      )}
                      <ArrowRight />
                    </LeftColumn>
                  ),
                  targetItem: (() => {
                    const targetFlowNodeId = itemMapping[sourceItem.id] ?? '';

                    return (
                      <Stack orientation="horizontal" gap={4}>
                        <Select
                          disabled={
                            processInstanceMigrationStore.state.currentStep ===
                              'summary' || selectableTargetItem.length === 0
                          }
                          size="sm"
                          hideLabel
                          labelText={`Target item for ${sourceItem.name}`}
                          id={sourceItem.id}
                          value={targetFlowNodeId}
                          onChange={({target}) => {
                            processInstanceMigrationStore.updateItemMapping({
                              sourceId: sourceItem.id,
                              targetId: target.value,
                            });
                          }}
                        >
                          {[{id: '', name: ''}, ...selectableTargetItem].map(
                            ({id, name}) => {
                              return (
                                <SelectItem
                                  key={id}
                                  value={id}
                                  text={name ?? id}
                                />
                              );
                            },
                          )}
                        </Select>
                        {isAutoMappable(sourceItem.id) &&
                          // show icon only when target item is selected
                          sourceItem.id === targetFlowNodeId && (
                            <IconContainer title="This item was automatically mapped">
                              <CheckmarkFilled data-testid="select-icon" />
                            </IconContainer>
                          )}
                      </Stack>
                    );
                  })(),
                };
              },
            )}
          />
        </>
      )}
    </BottomSection>
  );
});

export {BottomPanel};
