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

    return (
      <Popover
        referenceElement={selectedFlowNodeRef}
        offsetOptions={{
          offset: [0, 10],
        }}
        flipOptions={{
          fallbackPlacements: ['top', 'left', 'right'],
          boundary: diagramCanvasRef?.current ?? undefined,
        }}
      >
        <Title>Flow Node Modifications</Title>
        <Options>
          {processInstanceDetailsDiagramStore.appendableFlowNodes.includes(
            flowNodeId
          ) && (
            <Option
              title="Add single flow node instance"
              onClick={() => {
                modificationsStore.addModification({
                  type: 'token',
                  modification: {
                    operation: 'add',
                    flowNode: {
                      id: flowNodeId,
                      name: processInstanceDetailsDiagramStore.getFlowNodeName(
                        flowNodeId
                      ),
                    },
                    affectedTokenCount: 1,
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
                      modification: {
                        operation: 'cancel',
                        flowNode: {
                          id: flowNodeId,
                          name: processInstanceDetailsDiagramStore.getFlowNodeName(
                            flowNodeId
                          ),
                        },
                        affectedTokenCount: 1, //  TODO: This can only be set when instance counts are known #2926
                      },
                    });
                    flowNodeSelectionStore.clearSelection();
                  }}
                >
                  <CancelIcon />
                  Cancel
                </Option>
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
              </>
            )}
          {processInstanceDetailsDiagramStore.nonModifiableFlowNodes.includes(
            flowNodeId
          ) && <Unsupported>Unsupported flow node type</Unsupported>}
        </Options>
      </Popover>
    );
  }
);

export {ModificationDropdown};
