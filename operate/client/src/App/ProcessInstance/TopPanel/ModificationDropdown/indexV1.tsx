/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {Stack} from '@carbon/react';
import {flip, offset} from '@floating-ui/react-dom';
import {Add, ArrowRight, Error} from '@carbon/react/icons';
import isNil from 'lodash/isNil';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {modificationsStore} from 'modules/stores/modifications';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {tracking} from 'modules/tracking';
import {generateUniqueID} from 'modules/utils/generateUniqueID';
import {Popover} from 'modules/components/Popover';
import {
  Title,
  Unsupported,
  SelectedInstanceCount,
  Button,
  InlineLoading,
} from './styled';
import {
  clearSelection,
  getSelectedRunningInstanceCount,
} from 'modules/utils/flowNodeSelection';
import {
  useTotalRunningInstancesByFlowNode,
  useTotalRunningInstancesForFlowNode,
  useTotalRunningInstancesVisibleForFlowNode,
} from 'modules/queries/flownodeInstancesStatistics/useTotalRunningInstancesForFlowNode';
import {
  useAvailableModifications,
  useCanBeModified,
} from 'modules/hooks/modifications';
import {hasMultipleScopes} from 'modules/utils/processInstanceDetailsDiagram';
import {
  cancelAllTokens,
  generateParentScopeIds,
} from 'modules/utils/modifications';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';
import {getFlowNodeName} from 'modules/utils/flowNodes';
import {getParentElement} from 'modules/bpmn-js/utils/getParentElement';
import {
  useIsRootNodeSelected,
  useRootNode,
} from 'modules/hooks/flowNodeSelection';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';

type Props = {
  selectedFlowNodeRef?: SVGSVGElement;
  diagramCanvasRef?: React.RefObject<HTMLDivElement | null>;
};

const ModificationDropdown: React.FC<Props> = observer(
  ({selectedFlowNodeRef, diagramCanvasRef}) => {
    const flowNodeId = flowNodeSelectionStore.state.selection?.flowNodeId;
    const flowNodeInstanceId =
      flowNodeSelectionStore.state.selection?.flowNodeInstanceId ??
      flowNodeMetaDataStore.state.metaData?.flowNodeInstanceId;
    const {data: businessObjects} = useBusinessObjects();
    const {data: totalRunningInstances} =
      useTotalRunningInstancesForFlowNode(flowNodeId);
    const {data: totalRunningInstancesVisible} =
      useTotalRunningInstancesVisibleForFlowNode(flowNodeId);
    const {data: totalRunningInstancesByFlowNode} =
      useTotalRunningInstancesByFlowNode();
    const isRootNodeSelected = useIsRootNodeSelected();
    const selectedRunningInstanceCount = getSelectedRunningInstanceCount({
      totalRunningInstancesForFlowNode: totalRunningInstances || 0,
      isRootNodeSelected,
    });
    const availableModifications = useAvailableModifications({
      runningElementInstanceCount: selectedRunningInstanceCount,
      elementId: flowNodeId,
      elementInstanceKey:
        flowNodeSelectionStore.state.selection?.flowNodeInstanceId,
      isMultiInstanceBody:
        flowNodeSelectionStore.state.selection?.isMultiInstance,
      isElementInstanceKeyAvailable: !isNil(
        flowNodeSelectionStore.selectedFlowNodeInstanceId,
      ),
    });
    const canBeModified = useCanBeModified(flowNodeId);
    const rootNode = useRootNode();
    const {data: processInstance} = useProcessInstance();

    const processDefinitionKey = useProcessDefinitionKeyContext();
    const {data: processDefinitionData} = useProcessInstanceXml({
      processDefinitionKey,
    });

    if (
      flowNodeId === undefined ||
      modificationsStore.state.status === 'moving-token'
    ) {
      return null;
    }

    return (
      <Popover
        referenceElement={selectedFlowNodeRef}
        middlewareOptions={[
          offset(10),
          flip({
            fallbackPlacements: ['top', 'left', 'right'],
            boundary: diagramCanvasRef?.current ?? undefined,
          }),
        ]}
        variant="arrow"
        autoUpdatePosition
      >
        <Stack gap={3}>
          <Title>Flow Node Modifications</Title>
          <Stack gap={4}>
            {(() => {
              if (flowNodeMetaDataStore.state.status === 'fetching') {
                return <InlineLoading data-testid="dropdown-spinner" />;
              }
              if (!canBeModified) {
                return <Unsupported>Unsupported flow node type</Unsupported>;
              }

              if (availableModifications.length === 0) {
                return <Unsupported>No modifications available</Unsupported>;
              }

              return (
                <>
                  {selectedRunningInstanceCount > 0 && (
                    <SelectedInstanceCount>
                      Selected running instances: {selectedRunningInstanceCount}
                    </SelectedInstanceCount>
                  )}
                  <Stack gap={2}>
                    {availableModifications.includes('add') &&
                      businessObjects && (
                        <Button
                          kind="ghost"
                          title="Add single flow node instance"
                          aria-label="Add single flow node instance"
                          size="sm"
                          renderIcon={Add}
                          onClick={() => {
                            const parentElement = getParentElement(
                              processDefinitionData?.businessObjects?.[
                                flowNodeId
                              ],
                            );
                            if (
                              hasMultipleScopes(
                                parentElement,
                                totalRunningInstancesByFlowNode,
                              )
                            ) {
                              modificationsStore.startAddingToken(flowNodeId);
                            } else {
                              tracking.track({
                                eventName: 'add-token',
                              });

                              modificationsStore.addModification({
                                type: 'token',
                                payload: {
                                  operation: 'ADD_TOKEN',
                                  scopeId: generateUniqueID(),
                                  flowNode: {
                                    id: flowNodeId,
                                    name: getFlowNodeName({
                                      businessObjects,
                                      flowNodeId,
                                    }),
                                  },
                                  affectedTokenCount: 1,
                                  visibleAffectedTokenCount: 1,
                                  parentScopeIds: generateParentScopeIds(
                                    businessObjects,
                                    flowNodeId,
                                    processInstance?.processDefinitionId,
                                  ),
                                },
                              });
                            }

                            clearSelection(rootNode);
                          }}
                        >
                          Add
                        </Button>
                      )}

                    {availableModifications.includes('cancel-instance') &&
                      !isNil(flowNodeInstanceId) &&
                      businessObjects && (
                        <Button
                          kind="ghost"
                          title="Cancel selected instance in this flow node"
                          aria-label="Cancel selected instance in this flow node"
                          size="sm"
                          renderIcon={Error}
                          onClick={() => {
                            tracking.track({
                              eventName: 'cancel-token',
                            });

                            modificationsStore.cancelToken(
                              flowNodeId,
                              flowNodeInstanceId,
                              businessObjects,
                            );
                            clearSelection(rootNode);
                          }}
                        >
                          Cancel instance
                        </Button>
                      )}

                    {availableModifications.includes('cancel-all') &&
                      businessObjects && (
                        <Button
                          kind="ghost"
                          title="Cancel all running flow node instances in this flow node"
                          aria-label="Cancel all running flow node instances in this flow node"
                          size="sm"
                          renderIcon={Error}
                          onClick={() => {
                            tracking.track({
                              eventName: 'cancel-token',
                            });

                            cancelAllTokens(
                              flowNodeId,
                              totalRunningInstances ?? 0,
                              totalRunningInstancesVisible ?? 0,
                              businessObjects,
                            );
                            clearSelection(rootNode);
                          }}
                        >
                          Cancel all
                        </Button>
                      )}

                    {availableModifications.includes('move-instance') &&
                      !isNil(flowNodeInstanceId) && (
                        <Button
                          kind="ghost"
                          title="Move selected instance in this flow node to another target"
                          aria-label="Move selected instance in this flow node to another target"
                          size="sm"
                          renderIcon={ArrowRight}
                          onClick={() => {
                            modificationsStore.startMovingToken(
                              flowNodeId,
                              flowNodeInstanceId,
                            );
                            clearSelection(rootNode);
                          }}
                        >
                          Move instance
                        </Button>
                      )}
                    {availableModifications.includes('move-all') && (
                      <Button
                        kind="ghost"
                        title="Move all running instances in this flow node to another target"
                        size="sm"
                        renderIcon={ArrowRight}
                        onClick={() => {
                          modificationsStore.startMovingToken(flowNodeId);
                          clearSelection(rootNode);
                        }}
                      >
                        Move all
                      </Button>
                    )}
                  </Stack>
                </>
              );
            })()}
          </Stack>
        </Stack>
      </Popover>
    );
  },
);

export {ModificationDropdown};
