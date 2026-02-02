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
import {useEffect} from 'react';

type Props = {
  selectedFlowNodeRef?: SVGSVGElement;
  diagramCanvasRef?: React.RefObject<HTMLDivElement | null>;
};

// Module-level cache to track completed initial loads per element.
// Persists across component re-mounts (e.g., when Popover repositions).
const elementLoadingStateCache: Record<string, boolean> = {};

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
    const {
      data: selectedElementRunningInstancesCount,
      isFetching: isFetchingRunningCount,
    } = useTotalRunningInstancesForFlowNode(selectedElementId ?? undefined);
    const {data: totalRunningInstancesVisible} =
      useTotalRunningInstancesVisibleForFlowNode(
        selectedElementId ?? undefined,
      );
    const {
      data: totalRunningInstancesByFlowNode,
      isFetching: isFetchingByFlowNode,
    } = useTotalRunningInstancesByFlowNode();

    const availableModifications = useAvailableModifications({
      runningElementInstanceCount: selectedElementRunningInstancesCount ?? 0,
      elementId: selectedElementId ?? undefined,
      elementInstanceKey: selectedElementInstanceKey ?? undefined,
      isMultiInstanceBody: isSelectedInstanceMultiInstanceBody,
      isElementInstanceResolved: resolvedElementInstance !== null,
    });
    const canBeModified = useCanBeModified(selectedElementId ?? undefined);
    const {data: processInstance} = useProcessInstance();

    const processDefinitionKey = useProcessDefinitionKeyContext();
    const {data: processDefinitionData} = useProcessInstanceXml({
      processDefinitionKey,
    });

    const elementKey = selectedElementId ?? 'null';
    const hasCompletedInitialLoad =
      elementLoadingStateCache[elementKey] ?? false;

    // Mark inital load as complete when all queries finish
    useEffect(() => {
      if (
        !isFetchingElement &&
        !isFetchingByFlowNode &&
        !isFetchingRunningCount &&
        !hasCompletedInitialLoad
      ) {
        elementLoadingStateCache[elementKey] = true;
      }
    }, [
      isFetchingElement,
      isFetchingByFlowNode,
      isFetchingRunningCount,
      hasCompletedInitialLoad,
      elementKey,
    ]);

    const isInitiallyLoading =
      !hasCompletedInitialLoad &&
      (isFetchingElement || isFetchingByFlowNode || isFetchingRunningCount);

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
              if (isInitiallyLoading) {
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
                      !isNil(selectedElementInstanceKey) &&
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
                              selectedElementInstanceKey,
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
                      !isNil(selectedElementInstanceKey) && (
                        <Button
                          kind="ghost"
                          title="Move selected instance in this flow node to another target"
                          aria-label="Move selected instance in this flow node to another target"
                          size="sm"
                          renderIcon={ArrowRight}
                          onClick={() => {
                            modificationsStore.startMovingToken(
                              selectedElementId,
                              selectedElementInstanceKey,
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
