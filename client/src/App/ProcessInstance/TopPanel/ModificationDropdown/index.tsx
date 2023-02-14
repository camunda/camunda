/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  Popover,
  Title,
  Options,
  Option,
  MoveIcon,
  AddIcon,
  CancelIcon,
  Unsupported,
  SelectedInstanceCount,
  Spinner,
} from './styled';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {observer} from 'mobx-react';
import {modificationsStore} from 'modules/stores/modifications';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {generateUniqueID} from 'modules/utils/generateUniqueID';
import {tracking} from 'modules/tracking';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {modificationRulesStore} from 'modules/stores/modificationRules';
import {isNil} from 'lodash';
import {flip, offset} from '@floating-ui/react-dom';

type Props = {
  selectedFlowNodeRef?: SVGSVGElement;
  diagramCanvasRef?: React.RefObject<HTMLDivElement>;
};

const ModificationDropdown: React.FC<Props> = observer(
  ({selectedFlowNodeRef, diagramCanvasRef}) => {
    const flowNodeId = flowNodeSelectionStore.state.selection?.flowNodeId;
    const flowNodeInstanceId =
      flowNodeSelectionStore.state.selection?.flowNodeInstanceId ??
      flowNodeMetaDataStore.state.metaData?.flowNodeInstanceId;

    if (
      flowNodeId === undefined ||
      modificationsStore.state.status === 'moving-token'
    ) {
      return null;
    }

    const {selectedRunningInstanceCount} = flowNodeSelectionStore;
    const {canBeModified, availableModifications} = modificationRulesStore;

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
        <Title>Flow Node Modifications</Title>
        <Options>
          {(() => {
            if (flowNodeMetaDataStore.state.status === 'fetching') {
              return <Spinner data-testid="dropdown-spinner" />;
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
                {availableModifications.includes('add') && (
                  <Option
                    title="Add single flow node instance"
                    aria-label="Add single flow node instance"
                    onClick={() => {
                      if (
                        processInstanceDetailsDiagramStore.hasMultipleScopes(
                          processInstanceDetailsDiagramStore.getParentFlowNode(
                            flowNodeId
                          )
                        )
                      ) {
                        modificationsStore.startAddingToken();
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
                              name: processInstanceDetailsDiagramStore.getFlowNodeName(
                                flowNodeId
                              ),
                            },
                            affectedTokenCount: 1,
                            visibleAffectedTokenCount: 1,
                            parentScopeIds:
                              modificationsStore.generateParentScopeIds(
                                flowNodeId
                              ),
                          },
                        });
                      }

                      flowNodeSelectionStore.clearSelection();
                    }}
                  >
                    <AddIcon />
                    Add
                  </Option>
                )}

                {availableModifications.includes('cancel-instance') &&
                  !isNil(flowNodeInstanceId) && (
                    <Option
                      title="Cancel selected instance in this flow node"
                      onClick={() => {
                        tracking.track({
                          eventName: 'cancel-token',
                        });

                        modificationsStore.cancelToken(
                          flowNodeId,
                          flowNodeInstanceId
                        );
                        flowNodeSelectionStore.clearSelection();
                      }}
                    >
                      <CancelIcon />
                      Cancel instance
                    </Option>
                  )}

                {availableModifications.includes('cancel-all') && (
                  <Option
                    title="Cancel all running flow node instances in this flow node"
                    onClick={() => {
                      tracking.track({
                        eventName: 'cancel-token',
                      });

                      modificationsStore.cancelAllTokens(flowNodeId);
                      flowNodeSelectionStore.clearSelection();
                    }}
                  >
                    <CancelIcon />
                    Cancel all
                  </Option>
                )}

                {availableModifications.includes('move-instance') &&
                  !isNil(flowNodeInstanceId) && (
                    <Option
                      title="Move selected instance in this flow node to another target"
                      onClick={() => {
                        modificationsStore.startMovingToken(
                          flowNodeId,
                          flowNodeInstanceId
                        );
                        flowNodeSelectionStore.clearSelection();
                      }}
                    >
                      <MoveIcon />
                      Move instance
                    </Option>
                  )}
                {availableModifications.includes('move-all') && (
                  <Option
                    title="Move all running instances in this flow node to another target"
                    onClick={() => {
                      modificationsStore.startMovingToken(flowNodeId);
                      flowNodeSelectionStore.clearSelection();
                    }}
                  >
                    <MoveIcon />
                    Move all
                  </Option>
                )}
              </>
            );
          })()}
        </Options>
      </Popover>
    );
  }
);

export {ModificationDropdown};
