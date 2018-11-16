import React from 'react';
import {shallow} from 'enzyme';
import IncidentsByWorkflow from './IncidentsByWorkflow';
import Collapse from './Collapse';
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
    expect(nodeIncidentStatistic.props().label).toContain(
      mockIncidentsByWorkflow[0].workflows[0].version
    );
    expect(nodeIncidentStatistic.props().incidentsCount).toBe(
      mockIncidentsByWorkflow[0].instancesWithActiveIncidentsCount
    );
    expect(nodeIncidentStatistic.props().activeCount).toBe(
      mockIncidentsByWorkflow[0].activeInstancesCount
    );

    expect(nodeIncidentStatistic).toMatchSnapshot();
  });

  it('should render a statistics/collapsable statistics based on number of versions', () => {
    const node = shallow(
      <IncidentsByWorkflow incidents={mockIncidentsByWorkflow} />
    );
    const firstStatistic = node.find('[data-test="incident-byWorkflow-0"]');
    const secondStatistic = node.find('[data-test="incident-byWorkflow-1"]');
    expect(firstStatistic.find(Styled.IncidentStatistic).length).toBe(1);
    expect(secondStatistic.find(Collapse).length).toBe(1);
  });

  it('passes the right data to the statistics collapse', () => {
    const node = shallow(
      <IncidentsByWorkflow incidents={mockIncidentsByWorkflow} />
    );
    const secondStatistic = node.find('[data-test="incident-byWorkflow-1"]');
    const collapseNode = secondStatistic.find(Collapse);
    const headerCollapseNode = collapseNode.props().header;
    const contentCollapseNode = collapseNode.props().content;
    const headerNode = shallow(headerCollapseNode);
    const contentNode = shallow(contentCollapseNode);

    expect(headerNode.props().label).toContain(
      mockIncidentsByWorkflow[1].workflowName ||
        mockIncidentsByWorkflow[1].bpmnProcessId
    );
    expect(headerNode.props().label).toContain(
      mockIncidentsByWorkflow[1].workflows[0].version
    );
    expect(headerNode.props().label).toContain(
      mockIncidentsByWorkflow[1].workflows[1].version
    );
    expect(headerNode.props().incidentsCount).toBe(
      mockIncidentsByWorkflow[1].instancesWithActiveIncidentsCount
    );
    expect(headerNode.props().activeCount).toBe(
      mockIncidentsByWorkflow[1].activeInstancesCount
    );
    // should render a list with 2 items
    expect(contentNode.type()).toBe('ul');
    expect(contentNode.find(Styled.VersionLi).length).toBe(2);

    // should render two statistics
    expect(contentNode.find(Styled.IncidentStatistic).length).toBe(2);
    // should pass the right props to a statistic

    const versionStatisticNode = contentNode
      .find(Styled.IncidentStatistic)
      .at(0);

    expect(versionStatisticNode.props().label).toContain(
      `Version ${mockIncidentsByWorkflow[1].workflows[0].version}`
    );
    expect(versionStatisticNode.props().perUnit).toBe(true);
    expect(versionStatisticNode.props().incidentsCount).toBe(
      mockIncidentsByWorkflow[1].workflows[0].instancesWithActiveIncidentsCount
    );
    expect(versionStatisticNode.props().activeCount).toBe(
      mockIncidentsByWorkflow[1].workflows[0].activeInstancesCount
    );
  });
});
