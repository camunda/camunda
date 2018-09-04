import React from 'react';
import {shallow} from 'enzyme';

import Pane from 'modules/components/SplitPane/Pane';
import Diagram from 'modules/components/Diagram';
import StateIcon from 'modules/components/StateIcon';
import {formatDate} from 'modules/utils/date';
import {getWorkflowName} from 'modules/utils/instance';
import {INSTANCE_STATE} from 'modules/constants';

import DiagramPanel from './DiagramPanel';
import * as Styled from './styled';
import DiagramBar from './DiagramBar';

const mockInstance = {
  id: 'foo',
  workflowId: 'bar',
  startDate: 'Wed Jun 20 2018 08:57:20',
  endDate: formatDate(null),
  state: INSTANCE_STATE.ACTIVE
};

const mockProps = {
  instance: mockInstance,
  onFlowNodesDetailsReady: jest.fn(),
  selectableFlowNodes: ['foo'],
  selectedFlowNode: 'foo',
  onFlowNodeSelected: jest.fn(),
  flowNodeStateOverlays: [{}]
};

describe('DiagramPanel', () => {
  it('should render pane header and body', () => {
    // given
    const formattedStartDate = formatDate(mockInstance.startDate);
    const formattedEndDate = formatDate(mockInstance.endDate);
    const node = shallow(<DiagramPanel {...mockProps} />);

    // then
    expect(node.find(Pane)).toHaveLength(1);

    // Pane.Header
    const PaneHeaderNode = node.find(Pane.Header);
    expect(PaneHeaderNode).toHaveLength(1);
    const StyledTableNode = PaneHeaderNode.find(Styled.Table);
    expect(StyledTableNode).toHaveLength(1);
    const StateIconNode = StyledTableNode.find(StateIcon);
    expect(StateIconNode).toHaveLength(1);
    expect(StateIconNode.prop('instance')).toBe(mockInstance);
    expect(StyledTableNode.dive().text()).toContain(
      getWorkflowName(mockInstance)
    );
    expect(node.find(Pane.Body)).toHaveLength(1);
    expect(StyledTableNode.dive().text()).toContain(mockInstance.id);
    expect(StyledTableNode.dive().text()).toContain(formattedStartDate);
    expect(StyledTableNode.dive().text()).toContain(formattedEndDate);

    // Pane.Body
    const PaneBodyNode = node.find(Pane.Body);
    expect(PaneBodyNode).toHaveLength(1);
    // DiagramBar
    const DiagramBarNode = PaneBodyNode.find(DiagramBar);
    expect(DiagramBarNode).toHaveLength(1);
    expect(DiagramBarNode.prop('instance')).toBe(mockInstance);
    // DiagramNode
    const DiagramNode = PaneBodyNode.find(Diagram);
    expect(DiagramNode).toHaveLength(1);
    expect(DiagramNode.prop('workflowId')).toBe(mockInstance.workflowId);
    expect(DiagramNode.prop('onFlowNodesDetailsReady')).toBe(
      mockProps.onFlowNodesDetailsReady
    );
    expect(DiagramNode.prop('selectableFlowNodes')).toBe(
      mockProps.selectableFlowNodes
    );
    expect(DiagramNode.prop('selectedFlowNode')).toBe(
      mockProps.selectedFlowNode
    );
    expect(DiagramNode.prop('onFlowNodeSelected')).toBe(
      mockProps.onFlowNodeSelected
    );
    expect(DiagramNode.prop('flowNodeStateOverlays')).toBe(
      mockProps.flowNodeStateOverlays
    );
    expect(node).toMatchSnapshot();
  });
});
