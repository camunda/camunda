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
} from './styled';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {observer} from 'mobx-react';
import {modificationsStore} from 'modules/stores/modifications';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {generateUniqueID} from 'modules/utils/generateUniqueID';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {isMultiInstance} from 'modules/bpmn-js/isMultiInstance';

type Props = {
  selectedFlowNodeRef?: SVGSVGElement;
  diagramCanvasRef?: React.RefObject<HTMLDivElement>;
};

const ModificationDropdown: React.FC<Props> = observer(
  ({selectedFlowNodeRef, diagramCanvasRef}) => {
    const flowNodeId = flowNodeSelectionStore.state.selection?.flowNodeId;

    if (
      flowNodeId === undefined ||
      modificationsStore.state.status === 'moving-token'
    ) {
      return null;
    }

    const element = processInstanceDetailsDiagramStore.getFlowNode(flowNodeId);

    const canSelectedFlowNodeBeModified =
      !processInstanceDetailsDiagramStore.nonModifiableFlowNodes.includes(
        flowNodeId
      ) &&
      !(
        isMultiInstance(element) &&
        !flowNodeSelectionStore.state.selection?.isMultiInstance
      );

    return (
      <Popover
        key={flowNodeSelectionStore.state.selection?.flowNodeInstanceId}
        referenceElement={selectedFlowNodeRef}
        offsetOptions={[10]}
        flipOptions={[
          {
            fallbackPlacements: ['top', 'left', 'right'],
            boundary: diagramCanvasRef?.current ?? undefined,
          },
        ]}
        variant="arrow"
      >
        <Title>Flow Node Modifications</Title>
        <Options>
          {!canSelectedFlowNodeBeModified ? (
            <Unsupported>Unsupported flow node type</Unsupported>
          ) : (
            <>
              {processInstanceDetailsDiagramStore.appendableFlowNodes.includes(
                flowNodeId
              ) && (
                <Option
                  title="Add single flow node instance"
                  onClick={() => {
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
                          modificationsStore.generateParentScopeIds(flowNodeId),
                      },
                    });
                    flowNodeSelectionStore.clearSelection();
                  }}
                >
                  <AddIcon />
                  Add
                </Option>
              )}
              {processInstanceDetailsDiagramStore.cancellableFlowNodes.includes(
                flowNodeId
              ) &&
                !modificationsStore.isCancelModificationAppliedOnFlowNode(
                  flowNodeId
                ) && (
                  <>
                    <Option
                      title="Cancel all running flow node instances in this flow node"
                      onClick={() => {
                        modificationsStore.addModification({
                          type: 'token',
                          payload: {
                            operation: 'CANCEL_TOKEN',
                            flowNode: {
                              id: flowNodeId,
                              name: processInstanceDetailsDiagramStore.getFlowNodeName(
                                flowNodeId
                              ),
                            },
                            affectedTokenCount:
                              processInstanceDetailsStatisticsStore.getTotalRunningInstancesForFlowNode(
                                flowNodeId
                              ),
                            visibleAffectedTokenCount:
                              processInstanceDetailsStatisticsStore.getTotalRunningInstancesVisibleForFlowNode(
                                flowNodeId
                              ),
                          },
                        });
                        flowNodeSelectionStore.clearSelection();
                      }}
                    >
                      <CancelIcon />
                      Cancel
                    </Option>
                    {processInstanceDetailsDiagramStore.getFlowNode(flowNodeId)
                      ?.$type !== 'bpmn:SubProcess' && (
                      <Option
                        title="Move all running instances in this flow node to another target"
                        onClick={() => {
                          modificationsStore.startMovingToken(flowNodeId);
                          flowNodeSelectionStore.clearSelection();
                        }}
                      >
                        <MoveIcon />
                        Move
                      </Option>
                    )}
                  </>
                )}
            </>
          )}
        </Options>
      </Popover>
    );
  }
);

export {ModificationDropdown};
