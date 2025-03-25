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
import {processXmlStore as processXmlMigrationSourceStore} from 'modules/stores/processXml/processXml.migration.source';
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
  SourceFlowNodeName,
  ArrowRight,
  ToggleContainer,
} from './styled';
import {useMigrationTargetXml} from 'modules/queries/processDefinitions/useMigrationTargetXml';
import {processesStore} from 'modules/stores/processes/processes.migration';

const TOGGLE_LABEL = 'Show only not mapped';

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

  const {
    toggleMappedFilter,
    state: {isMappedFilterEnabled},
    getMappableFlowNodes,
  } = processInstanceMigrationMappingStore;

  const {selectedTargetProcessId} = processesStore;
  const isTargetSelected = !!selectedTargetProcessId;

  const {data} = useMigrationTargetXml({
    processDefinitionKey: selectedTargetProcessId!,
    enabled: selectedTargetProcessId !== undefined,
  });

  const mappableFlowNodes = getMappableFlowNodes(data?.selectableFlowNodes);

  /**
   * Flow nodes which are contained in both source diagram and target diagram.
   *
   * A flow node is auto-mappable when
   * - the flow node id is contained in source and target diagram
   * - the bpmn type is matching in source and target diagram
   */

  const autoMappableFlowNodes = useMemo(() => {
    if (data === undefined) return [];

    return processXmlMigrationSourceStore.selectableFlowNodes
      .filter((sourceFlowNode) => {
        const targetFlowNode = data.selectableFlowNodes.find(
          (flowNode) => flowNode.id === sourceFlowNode.id,
        );

        return (
          targetFlowNode !== undefined &&
          sourceFlowNode.$type === targetFlowNode.$type
        );
      })
      .map(({id, $type}) => {
        return {id, type: $type};
      });
  }, [data]);

  /**
   * Returns true if the flow node with flowNodeId is auto-mappable
   */
  const isAutoMappable = (flowNodeId: string) => {
    return (
      autoMappableFlowNodes.find(({id}) => {
        return flowNodeId === id;
      }) !== undefined
    );
  };

  const {hasSelectableFlowNodes: hasSelectableSourceFlowNodes} =
    processXmlMigrationSourceStore;

  const filteredSourceFlowNodeMappings = mappableFlowNodes.filter(
    ({sourceFlowNode}) => {
      return (
        !isMappedFilterEnabled ||
        flowNodeMapping[sourceFlowNode.id] === undefined
      );
    },
  );

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

  useEffect(() => {
    // reset store on unmount
    return processInstanceMigrationMappingStore.reset;
  }, []);

  return (
    <BottomSection>
      {!hasSelectableSourceFlowNodes ? (
        <ErrorMessageContainer>
          <ErrorMessage
            message="There are no mappable flow nodes"
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
            rows={filteredSourceFlowNodeMappings.map(
              ({sourceFlowNode, selectableTargetFlowNodes}) => {
                const isMapped =
                  flowNodeMapping[sourceFlowNode.id] !== undefined;

                return {
                  id: sourceFlowNode.id,
                  sourceFlowNode: (
                    <LeftColumn>
                      <SourceFlowNodeName>
                        {sourceFlowNode.name}
                      </SourceFlowNodeName>
                      {isTargetSelected && !isMapped && (
                        <Tag type="blue">Not mapped</Tag>
                      )}
                      <ArrowRight />
                    </LeftColumn>
                  ),
                  targetFlowNode: (() => {
                    const targetFlowNodeId =
                      flowNodeMapping[sourceFlowNode.id] ?? '';

                    return (
                      <Stack orientation="horizontal" gap={4}>
                        <Select
                          disabled={
                            processInstanceMigrationStore.state.currentStep ===
                              'summary' ||
                            selectableTargetFlowNodes.length === 0
                          }
                          size="sm"
                          hideLabel
                          labelText={`Target flow node for ${sourceFlowNode.name}`}
                          id={sourceFlowNode.id}
                          value={targetFlowNodeId}
                          onChange={({target}) => {
                            processInstanceMigrationStore.updateFlowNodeMapping(
                              {
                                sourceId: sourceFlowNode.id,
                                targetId: target.value,
                              },
                            );
                          }}
                        >
                          {[
                            {id: '', name: ''},
                            ...selectableTargetFlowNodes,
                          ].map(({id, name}) => {
                            return (
                              <SelectItem key={id} value={id} text={name} />
                            );
                          })}
                        </Select>
                        {isAutoMappable(sourceFlowNode.id) &&
                          // show icon only when target flow node is selected
                          sourceFlowNode.id === targetFlowNodeId && (
                            <IconContainer title="This flow node was automatically mapped">
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
