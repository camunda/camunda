import React from 'react';
import {shallow} from 'enzyme';

import Pane from 'modules/components/SplitPane/Pane';
import Diagram from 'modules/components/Diagram';
import StateIcon from 'modules/components/StateIcon';
import {formatDate} from 'modules/utils/date';
import {getWorkflowName} from 'modules/utils/instance';
import {createInstance} from 'modules/testUtils';

import DiagramPanel from './DiagramPanel';
import * as Styled from './styled';
import DiagramBar from './DiagramBar';

const mockInstance = createInstance({id: 'foo'});

const mockProps = {
  instance: mockInstance,
  onDiagramLoaded: jest.fn(),
  selectableFlowNodes: ['foo'],
  selectedFlowNode: 'foo',
  onFlowNodeSelected: jest.fn(),
  flowNodeStateOverlays: [{}]
};

describe.skip('DiagramPanel', () => {
  it('should render pane header and body', () => {
    // given
    const formattedStartDate = formatDate(mockInstance.startDate);
    const formattedEndDate = formatDate(mockInstance.endDate);
    const node = shallow(<DiagramPanel {...mockProps} />);

    // then
    expect(node.find(Pane)).toHaveLength(1);
    expect(node.find(Pane).props().hasShiftableControls).toBe(false);

    // Pane.Header
    const PaneHeaderNode = node.find(Styled.SplitPaneHeader);
    expect(PaneHeaderNode).toHaveLength(1);
    const StyledTableNode = PaneHeaderNode.find(Styled.Table);
    expect(StyledTableNode).toHaveLength(1);
    const StateIconNode = StyledTableNode.find(StateIcon);
    expect(StateIconNode).toHaveLength(1);
    expect(StateIconNode.prop('instance')).toBe(mockInstance);
    expect(StyledTableNode.text()).toContain(getWorkflowName(mockInstance));
    expect(node.find(Pane.Body)).toHaveLength(1);
    expect(StyledTableNode.text()).toContain(mockInstance.id);
    expect(StyledTableNode.text()).toContain(
      `Version ${mockInstance.workflowVersion}`
    );
    expect(StyledTableNode.text()).toContain(formattedStartDate);
    expect(StyledTableNode.text()).toContain(formattedEndDate);

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
    expect(DiagramNode.prop('onDiagramLoaded')).toBe(mockProps.onDiagramLoaded);
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
