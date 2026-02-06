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
import {modificationsStore} from 'modules/stores/modifications';
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
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';

type Props = {
  selectedFlowNodeRef?: SVGSVGElement;
  diagramCanvasRef?: React.RefObject<HTMLDivElement | null>;
};

const ModificationDropdown: React.FC<Props> = observer(
  ({selectedFlowNodeRef, diagramCanvasRef}) => {
    const {
      selectedElementId,
      selectedElementInstanceKey,
      isFetchingElement,
      isSelectedInstanceMultiInstanceBody,
      resolvedElementInstance,
      clearSelection,
    } = useProcessInstanceElementSelection();
    const {data: businessObjects} = useBusinessObjects();
    const {data: totalRunningInstancesCount} =
      useTotalRunningInstancesForFlowNode(selectedElementId ?? undefined);
    const {data: totalRunningInstancesVisible} =
      useTotalRunningInstancesVisibleForFlowNode(
        selectedElementId ?? undefined,
      );
    const {data: totalRunningInstancesByFlowNode} =
      useTotalRunningInstancesByFlowNode();
    const selectedElementRunningInstancesCount =
      resolvedElementInstance === null ? totalRunningInstancesCount : 1;

    const resolvedElementInstanceKey =
      resolvedElementInstance?.elementInstanceKey;

    const availableModifications = useAvailableModifications({
      runningElementInstanceCount: selectedElementRunningInstancesCount ?? 0,
      elementId: selectedElementId ?? undefined,
      elementInstanceKey: selectedElementInstanceKey ?? undefined,
      isMultiInstanceBody: isSelectedInstanceMultiInstanceBody,
      isElementInstanceResolved:
        selectedElementInstanceKey !== null ||
        resolvedElementInstanceKey !== undefined,
    });
    const canBeModified = useCanBeModified(selectedElementId ?? undefined);
    const {data: processInstance} = useProcessInstance();

    const processDefinitionKey = useProcessDefinitionKeyContext();
    const {data: processDefinitionData} = useProcessInstanceXml({
      processDefinitionKey,
    });

    if (
      selectedElementId === null ||
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
              if (isFetchingElement) {
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
                  {(selectedElementRunningInstancesCount ?? 0) > 0 && (
                    <SelectedInstanceCount>
                      {`Selected running instances: ${selectedElementRunningInstancesCount ?? 0}`}
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
                                selectedElementId
                              ],
                            );
                            if (
                              hasMultipleScopes(
                                parentElement,
                                totalRunningInstancesByFlowNode,
                              )
                            ) {
                              modificationsStore.startAddingToken(
                                selectedElementId,
                              );
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
                                    id: selectedElementId,
                                    name: getFlowNodeName({
                                      businessObjects,
                                      flowNodeId: selectedElementId,
                                    }),
                                  },
                                  affectedTokenCount: 1,
                                  visibleAffectedTokenCount: 1,
                                  parentScopeIds: generateParentScopeIds(
                                    businessObjects,
                                    selectedElementId,
                                    processInstance?.processDefinitionId,
                                  ),
                                },
                              });
                            }
                            clearSelection();
                          }}
                        >
                          Add
                        </Button>
                      )}

                    {availableModifications.includes('cancel-instance') &&
                      !isNil(resolvedElementInstanceKey) &&
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
                              selectedElementId,
                              resolvedElementInstanceKey,
                              businessObjects,
                            );
                            clearSelection();
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
                              selectedElementId,
                              selectedElementRunningInstancesCount ?? 0,
                              totalRunningInstancesVisible ?? 0,
                              businessObjects,
                            );
                            clearSelection();
                          }}
                        >
                          Cancel all
                        </Button>
                      )}

                    {availableModifications.includes('move-instance') &&
                      !isNil(resolvedElementInstanceKey) && (
                        <Button
                          kind="ghost"
                          title="Move selected instance in this flow node to another target"
                          aria-label="Move selected instance in this flow node to another target"
                          size="sm"
                          renderIcon={ArrowRight}
                          onClick={() => {
                            modificationsStore.startMovingToken(
                              selectedElementId,
                              resolvedElementInstanceKey,
                            );
                            clearSelection();
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
                          modificationsStore.startMovingToken(
                            selectedElementId,
                          );
                          clearSelection();
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
