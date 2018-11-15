import React from 'react';
import {shallow} from 'enzyme';
import IncidentsByWorkflow from './IncidentsByWorkflow';
import * as Styled from './styled';

const mockIncidentsByWorkflow = [
  {
    bpmnProcessId: 'loanProcess',
    workflowName: null,
    instancesWithActiveIncidentsCount: 16,
    activeInstancesCount: 122,
    workflows: [
      {
        workflowId: '3',
        version: 1,
        name: null,
        errorMessage: null,
        instancesWithActiveIncidentsCount: 16,
        activeInstancesCount: 122
      }
    ]
  },
  {
    bpmnProcessId: 'orderProcess',
    workflowName: 'Order process',
    instancesWithActiveIncidentsCount: 65,
    activeInstancesCount: 136,
    workflows: [
      {
        workflowId: '2',
        version: 1,
        name: 'Order process',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 53,
        activeInstancesCount: 13
      },
      {
        workflowId: '5',
        version: 2,
        name: 'Order process',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 12,
        activeInstancesCount: 123
      }
    ]
  }
];

describe('IncidentsByWorkflow', () => {
  it('should render a list', () => {
    const node = shallow(
      <IncidentsByWorkflow incidents={mockIncidentsByWorkflow} />
    );
    expect(node.type()).toBe('ul');
  });
  it('should render an li for each incident statistic', () => {
    const node = shallow(
      <IncidentsByWorkflow incidents={mockIncidentsByWorkflow} />
    );

    expect(node.find(Styled.Li).length).toBe(mockIncidentsByWorkflow.length);
  });

  it('should pass the right data to IncidentStatistic', () => {
    const node = shallow(
      <IncidentsByWorkflow incidents={mockIncidentsByWorkflow} />
    );
    const nodeIncidentStatistic = node.find(Styled.IncidentStatistic).at(0);

    expect(nodeIncidentStatistic.props().label).toContain(
      mockIncidentsByWorkflow[0].workflowName ||
        mockIncidentsByWorkflow[0].bpmnProcessId
    );
    expect(nodeIncidentStatistic.props().incidentsCount).toBe(
      mockIncidentsByWorkflow[0].instancesWithActiveIncidentsCount
    );
    expect(nodeIncidentStatistic.props().activeCount).toBe(
      mockIncidentsByWorkflow[0].activeInstancesCount
    );

    expect(nodeIncidentStatistic).toMatchSnapshot();
  });
});
