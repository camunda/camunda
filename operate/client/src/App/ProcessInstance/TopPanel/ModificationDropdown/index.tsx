/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {observer} from 'mobx-react';
import {Stack} from '@carbon/react';
import {flip, offset} from '@floating-ui/react-dom';
import {Add, ArrowRight, Error} from '@carbon/react/icons';
import isNil from 'lodash/isNil';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {modificationsStore} from 'modules/stores/modifications';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {modificationRulesStore} from 'modules/stores/modificationRules';
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
                    {availableModifications.includes('add') && (
                      <Button
                        kind="ghost"
                        title="Add single flow node instance"
                        aria-label="Add single flow node instance"
                        size="sm"
                        renderIcon={Add}
                        onClick={() => {
                          if (
                            processInstanceDetailsDiagramStore.hasMultipleScopes(
                              processInstanceDetailsDiagramStore.getParentFlowNode(
                                flowNodeId,
                              ),
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
                                  name: processInstanceDetailsDiagramStore.getFlowNodeName(
                                    flowNodeId,
                                  ),
                                },
                                affectedTokenCount: 1,
                                visibleAffectedTokenCount: 1,
                                parentScopeIds:
                                  modificationsStore.generateParentScopeIds(
                                    flowNodeId,
                                  ),
                              },
                            });
                          }

                          flowNodeSelectionStore.clearSelection();
                        }}
                      >
                        Add
                      </Button>
                    )}

                    {availableModifications.includes('cancel-instance') &&
                      !isNil(flowNodeInstanceId) && (
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
                            );
                            flowNodeSelectionStore.clearSelection();
                          }}
                        >
                          Cancel instance
                        </Button>
                      )}

                    {availableModifications.includes('cancel-all') && (
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

                          modificationsStore.cancelAllTokens(flowNodeId);
                          flowNodeSelectionStore.clearSelection();
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
                            flowNodeSelectionStore.clearSelection();
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
                          flowNodeSelectionStore.clearSelection();
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
