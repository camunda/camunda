/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import IncidentsByWorkflow from './IncidentsByWorkflow';
import IncidentByWorkflow from './IncidentByWorkflow';
import Collapse from '../Collapse';
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
        bpmnProcessId: 'loanProcess',
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
        bpmnProcessId: 'orderProcess',
        workflowId: '2',
        version: 1,
        name: 'Order process',
        errorMessage: null,
        instancesWithActiveIncidentsCount: 53,
        activeInstancesCount: 13
      },
      {
        bpmnProcessId: 'orderProcess',
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

  it('should pass the right data to IncidentByWorkflow', () => {
    const node = shallow(
      <IncidentsByWorkflow incidents={mockIncidentsByWorkflow} />
    );
    const nodeIncidentByWorkflow = node.find(IncidentByWorkflow).at(0);
    const statisticsAnchor = nodeIncidentByWorkflow.parent();

    expect(statisticsAnchor.props().to).toBe(
      '/instances?filter={"workflow":"loanProcess","version":"1","incidents":true}'
    );
    expect(statisticsAnchor.props().title).toBe(
      'View 138 Instances in 1 Version of Workflow loanProcess'
    );

    expect(nodeIncidentByWorkflow.props().label).toContain(
      mockIncidentsByWorkflow[0].workflowName ||
        mockIncidentsByWorkflow[0].bpmnProcessId
    );
    expect(nodeIncidentByWorkflow.props().label).toContain(
      mockIncidentsByWorkflow[0].workflows[0].version
    );
    expect(nodeIncidentByWorkflow.props().incidentsCount).toBe(
      mockIncidentsByWorkflow[0].instancesWithActiveIncidentsCount
    );
    expect(nodeIncidentByWorkflow.props().activeCount).toBe(
      mockIncidentsByWorkflow[0].activeInstancesCount
    );

    expect(nodeIncidentByWorkflow).toMatchSnapshot();
  });

  it('should render a statistics/collapsable statistics based on number of versions', () => {
    const node = shallow(
      <IncidentsByWorkflow incidents={mockIncidentsByWorkflow} />
    );
    const firstStatistic = node.find('[data-test="incident-byWorkflow-0"]');
    const secondStatistic = node.find('[data-test="incident-byWorkflow-1"]');
    expect(firstStatistic.find(IncidentByWorkflow).length).toBe(1);
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
    const headerStatistic = headerNode.find(IncidentByWorkflow);
    const contentNode = shallow(contentCollapseNode);

    // collapse button node
    expect(collapseNode.props().buttonTitle).toBe(
      'Expand 201 Instances of Workflow Order process'
    );

    // header anchor
    expect(headerNode.props().to).toBe(
      '/instances?filter={"workflow":"orderProcess","version":"all","incidents":true}'
    );
    expect(headerNode.props().title).toBe(
      'View 201 Instances in 2 Versions of Workflow Order process'
    );

    expect(headerStatistic.props().label).toContain(
      mockIncidentsByWorkflow[1].workflowName ||
        mockIncidentsByWorkflow[1].bpmnProcessId
    );
    expect(headerStatistic.props().label).toContain(
      mockIncidentsByWorkflow[1].workflows[0].version
    );
    expect(headerStatistic.props().label).toContain(
      mockIncidentsByWorkflow[1].workflows[1].version
    );
    expect(headerStatistic.props().incidentsCount).toBe(
      mockIncidentsByWorkflow[1].instancesWithActiveIncidentsCount
    );
    expect(headerStatistic.props().activeCount).toBe(
      mockIncidentsByWorkflow[1].activeInstancesCount
    );
    // should render a list with 2 items
    expect(contentNode.type()).toBe('ul');
    expect(contentNode.find(Styled.VersionLi).length).toBe(2);

    // should render two statistics
    expect(contentNode.find(IncidentByWorkflow).length).toBe(2);
    // should pass the right props to a statistic

    const versionStatisticNode = contentNode.find(IncidentByWorkflow).at(0);

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
