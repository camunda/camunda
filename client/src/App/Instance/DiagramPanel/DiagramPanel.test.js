import React from 'react';
import {shallow} from 'enzyme';

import Panel from 'modules/components/Panel';
import Diagram from 'modules/components/Diagram';
import StateIcon from 'modules/components/StateIcon';
import {formatDate, INSTANCE_STATE} from 'modules/utils';

import DiagramPanel from './DiagramPanel';
import * as Styled from './styled';
import DiagramBar from './DiagramBar';

describe('DiagramPanel', () => {
  let mockInstance = {
    id: 'foo',
    workflowId: 'bar',
    startDate: 'Wed Jun 20 2018 08:57:20',
    endDate: formatDate(null),
    state: INSTANCE_STATE.ACTIVE
  };

  it('should render panel header and body', () => {
    // given
    const formattedStartDate = formatDate(mockInstance.startDate);
    const formattedEndDate = formatDate(mockInstance.endDate);
    const node = shallow(<DiagramPanel instance={mockInstance} />);

    // then
    expect(node.find(Panel)).toHaveLength(1);

    const PanelHeaderNode = node.find(Panel.Header);
    expect(PanelHeaderNode).toHaveLength(1);
    const StyledPanelHeaderNode = PanelHeaderNode.find(
      Styled.DiagramPanelHeader
    );
    expect(StyledPanelHeaderNode).toHaveLength(1);
    const tbodyNode = StyledPanelHeaderNode.find('tbody');
    expect(tbodyNode).toHaveLength(1);
    const trNode = tbodyNode.find('tr');
    expect(trNode).toHaveLength(1);
    const tdNodes = trNode.find('td');
    expect(tdNodes).toHaveLength(4);
    const StateIconNode = tdNodes.at(0).find(StateIcon);
    expect(StateIconNode).toHaveLength(1);
    expect(StateIconNode.prop('instance')).toBe(mockInstance);
    expect(tdNodes.at(0).text()).toContain(mockInstance.workflowId);
    expect(node.find(Panel.Body)).toHaveLength(1);
    expect(tdNodes.at(2).text()).toContain(formattedStartDate);
    expect(tdNodes.at(3).text()).toContain(formattedEndDate);

    const PanelBodyNode = node.find(Panel.Body);
    expect(PanelBodyNode).toHaveLength(1);
    const DiagramBarNode = PanelBodyNode.find(DiagramBar);
    expect(DiagramBarNode).toHaveLength(1);
    expect(DiagramBarNode.prop('instance')).toBe(mockInstance);
    const DiagramNode = PanelBodyNode.find(Diagram);
    expect(DiagramNode).toHaveLength(1);
    expect(DiagramNode.prop('workflowId')).toBe(mockInstance.workflowId);
    expect(node).toMatchSnapshot();
  });
});
