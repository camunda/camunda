import React from 'react';
import {shallow} from 'enzyme';

import Panel from 'modules/components/Panel';
import Diagram from 'modules/components/Diagram';
import StateIcon from 'modules/components/StateIcon';
import {formatDate} from 'modules/utils';

import DiagramPanel from './DiagramPanel';
import * as Styled from './styled';

describe('DiagramPanel', () => {
  let mockInstance = {
    id: 'foo',
    workflowDefinitionId: 'bar',
    startDate: 'Wed Jun 20 2018 08:57:20',
    endDate: formatDate(null),
    stateName: 'ACTIVE'
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
    expect(StateIconNode.prop('stateName')).toBe(mockInstance.stateName);
    expect(tdNodes.at(0).text()).toContain(mockInstance.workflowDefinitionId);
    expect(node.find(Panel.Body)).toHaveLength(1);
    expect(tdNodes.at(2).text()).toContain(formattedStartDate);
    expect(tdNodes.at(3).text()).toContain(formattedEndDate);

    const PanelBodyNode = node.find(Panel.Body);
    expect(PanelBodyNode).toHaveLength(1);
    const DiagramNode = PanelBodyNode.find(Diagram);
    expect(DiagramNode).toHaveLength(1);
    expect(DiagramNode.prop('workflowDefinitionId')).toBe(
      mockInstance.workflowDefinitionId
    );
    expect(node).toMatchSnapshot();
  });

  it('should show incident when there is one', () => {
    // given
    mockInstance = {
      ...mockInstance,
      stateName: 'INCIDENT',
      errorMessage: 'error'
    };
    const node = shallow(<DiagramPanel instance={mockInstance} />);

    // then
    const PanelBodyNode = node.find(Panel.Body);
    const StyledIncidentMessageNode = PanelBodyNode.find(
      Styled.IncidentMessage
    );
    expect(StyledIncidentMessageNode).toHaveLength(1);
    expect(StyledIncidentMessageNode.dive().text()).toContain('Incident:');
    expect(StyledIncidentMessageNode.dive().text()).toContain(
      mockInstance.errorMessage
    );
    expect(node).toMatchSnapshot();
  });
});
