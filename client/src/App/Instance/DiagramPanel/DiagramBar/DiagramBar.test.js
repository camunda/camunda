import React from 'react';
import {shallow} from 'enzyme';

import DiagramBar from './DiagramBar';
import * as Styled from './styled';

describe('DiagramBar', () => {
  let mockInstance = {
    id: 'foo',
    workflowId: 'bar',
    stateName: 'ACTIVE'
  };
  it('should render null if there is no incident', () => {
    // given
    const node = shallow(<DiagramBar instance={mockInstance} />);

    // then
    expect(node.html()).toBe(null);
  });
  it('should render the incident message when there is one', () => {
    // given
    const mockErrorMessage = 'error';
    mockInstance = {
      ...mockInstance,
      state: 'ACTIVE',
      incidents: [{state: 'ACTIVE', errorMessage: mockErrorMessage}]
    };
    const node = shallow(<DiagramBar instance={mockInstance} />);

    // then
    const StyledIncidentMessageNode = node.find(Styled.IncidentMessage);
    expect(StyledIncidentMessageNode).toHaveLength(1);
    expect(StyledIncidentMessageNode.dive().text()).toContain('Incident:');
    expect(StyledIncidentMessageNode.dive().text()).toContain(mockErrorMessage);
    expect(node).toMatchSnapshot();
  });
});
