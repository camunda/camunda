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
} from './styled';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {observer} from 'mobx-react';

type Props = {
  selectedFlowNodeRef?: SVGSVGElement;
  diagramCanvasRef?: React.RefObject<HTMLDivElement>;
};

const ModificationDropdown: React.FC<Props> = observer(
  ({selectedFlowNodeRef, diagramCanvasRef}) => {
    const flowNodeId = flowNodeSelectionStore.state.selection?.flowNodeId;

    if (flowNodeId === undefined) {
      return null;
    }

    return (
      <Popover
        selectedFlowNodeRef={selectedFlowNodeRef}
        offsetOptions={{
          offset: [50, 10],
        }}
        flipOptions={{
          fallbackPlacements: ['top', 'left', 'right'],
          boundary: diagramCanvasRef?.current ?? undefined,
        }}
      >
        <Title>Flow Node Modifications</Title>
        <Options>
          <Option title="Add single flow node instance">
            <AddIcon />
            Add
          </Option>
          <Option title="Cancel all running flow node instances in this flow node">
            <CancelIcon />
            Cancel
          </Option>
          <Option title="Move all running instances in this flow node to another target">
            <MoveIcon />
            Move
          </Option>
        </Options>
      </Popover>
    );
  }
);

export {ModificationDropdown};
