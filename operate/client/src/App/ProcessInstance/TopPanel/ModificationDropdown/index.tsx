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
  InfoMessage,
  Button,
  InlineLoading,
} from './styled';
import {
  useTotalRunningInstancesByElement,
  useTotalRunningInstancesForElement,
  useTotalRunningInstancesVisibleForElement,
} from 'modules/queries/elementInstancesStatistics/useTotalRunningInstancesForElement';
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
import {getElementName} from 'modules/utils/elements';
import {getParentElement} from 'modules/bpmn-js/utils/getParentElement';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';

type Props = {
  selectedElementRef?: SVGSVGElement;
  diagramCanvasRef?: React.RefObject<HTMLDivElement | null>;
};

const ModificationDropdown: React.FC<Props> = observer(
  ({selectedElementRef, diagramCanvasRef}) => {
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
      useTotalRunningInstancesForElement(selectedElementId ?? undefined);
    const {data: totalRunningInstancesVisible} =
      useTotalRunningInstancesVisibleForElement(selectedElementId ?? undefined);
    const {data: totalRunningInstancesByElement} =
      useTotalRunningInstancesByElement();

    // true if an element instance is selected from the element history tree
    const isSpecificElementInstanceSelected =
      selectedElementInstanceKey !== null;

    // true if an element is selected from diagram or element history tree,
    // and a single specific running element instance is resolved for the selection
    const isResolvedInstanceRunning =
      resolvedElementInstance?.state === 'ACTIVE' ||
      resolvedElementInstance?.hasIncident;

    let selectedElementRunningInstancesCount = totalRunningInstancesCount;
    if (isSpecificElementInstanceSelected && !isResolvedInstanceRunning) {
      selectedElementRunningInstancesCount = 0;
    } else if (
      isResolvedInstanceRunning &&
      !isSelectedInstanceMultiInstanceBody
    ) {
      selectedElementRunningInstancesCount = 1;
    }

    const resolvedElementInstanceKey =
      resolvedElementInstance?.elementInstanceKey;

    const availableModifications = useAvailableModifications({
      runningElementInstanceCount: selectedElementRunningInstancesCount ?? 0,
      elementId: selectedElementId ?? undefined,
      isSpecificElementInstanceSelected,
      isMultiInstanceBody: isSelectedInstanceMultiInstanceBody,
      isSingleRunningInstanceResolved: isResolvedInstanceRunning,
    });
    const canBeModified = useCanBeModified(selectedElementId ?? undefined);
    const {data: processInstance} = useProcessInstance();

    const processDefinitionKey = useProcessDefinitionKeyContext();
    const {data: processDefinitionData} = useProcessInstanceXml({
      processDefinitionKey,
    });

    const hasSelectedElementMultipleRunningInstances =
      selectedElementRunningInstancesCount !== undefined &&
      selectedElementRunningInstancesCount > 1;

    if (
      selectedElementId === null ||
      modificationsStore.state.status === 'moving-token'
    ) {
      return null;
    }

    return (
      <Popover
        referenceElement={selectedElementRef}
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
          <Title>Element Modifications</Title>
          <Stack gap={4}>
            {(() => {
              if (isFetchingElement) {
                return <InlineLoading data-testid="dropdown-spinner" />;
              }
              if (!canBeModified) {
                return <Unsupported>Unsupported element type</Unsupported>;
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
                  {hasSelectedElementMultipleRunningInstances && (
                    <InfoMessage>
                      To modify a specific instance, select it in the Instance
                      History below.
                    </InfoMessage>
                  )}

                  <Stack gap={2}>
                    {availableModifications.includes('add') &&
                      businessObjects && (
                        <Button
                          kind="ghost"
                          title="Add single element instance"
                          aria-label="Add single element instance"
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
                                totalRunningInstancesByElement,
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
                                  element: {
                                    id: selectedElementId,
                                    name: getElementName({
                                      businessObjects,
                                      elementId: selectedElementId,
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
                          title="Cancel selected instance in this element"
                          aria-label="Cancel selected instance in this element"
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
                          title="Cancel all running element instances in this element"
                          aria-label="Cancel all running element instances in this element"
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
                          title="Move selected instance in this element to another target"
                          aria-label="Move selected instance in this element to another target"
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
                        title="Move all running instances in this element to another target"
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
