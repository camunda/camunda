/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import IncidentsByError from './IncidentsByError';
import IncidentByError from './IncidentByError';
import Collapse from '../Collapse';
import * as Styled from './styled';

import {
  createWorkflow,
  createIncidentsByError,
  createInstanceByError
} from 'modules/testUtils';

const InstancesByErrorMessage = [
  createInstanceByError({
    workflows: [createWorkflow()]
  }),
  createInstanceByError({
    errorMessage: 'JSON path $.paid has no result.',
    workflows: [createWorkflow(), createWorkflow()]
  })
];

const mockIncidentsByError = createIncidentsByError(InstancesByErrorMessage);

describe('IncidentsByError', () => {
  it('should render a list', () => {
    const node = shallow(<IncidentsByError incidents={mockIncidentsByError} />);

    expect(node.type()).toBe('ul');
  });

  it('should render an li for each incident statistic', () => {
    const node = shallow(<IncidentsByError incidents={mockIncidentsByError} />);

    expect(node.find(Styled.Li).length).toBe(mockIncidentsByError.length);
  });

  it('should render a collapsable statistic for each error message', () => {
    const node = shallow(<IncidentsByError incidents={mockIncidentsByError} />);
    const firstStatistic = node.find('[data-test="incident-byError-0"]');
    const secondStatistic = node.find('[data-test="incident-byError-1"]');
    expect(firstStatistic.find(Collapse).length).toBe(1);
    expect(secondStatistic.find(Collapse).length).toBe(1);
  });

  it('passes the right data to the statistics collapse', () => {
    const node = shallow(<IncidentsByError incidents={mockIncidentsByError} />);
    const secondStatistic = node.find('[data-test="incident-byError-1"]');

    const collapseNode = secondStatistic.find(Collapse);
    const headerCollapseNode = collapseNode.props().header;
    const contentCollapseNode = collapseNode.props().content;
    const headerNode = shallow(headerCollapseNode);
    const headerStatistic = headerNode.find(IncidentByError);
    const contentNode = shallow(contentCollapseNode);

    //collapse button node
    expect(collapseNode.props().buttonTitle).toBe(
      'Expand 36 Instances with error "JSON path $.paid has no result."'
    );

    // header anchor
    const encodedFilter = encodeURIComponent(
      '{"errorMessage":"JSON path $.paid has no result.","incidents":true}'
    );
    expect(headerNode.props().to).toBe(`/instances?filter=${encodedFilter}`);
    expect(headerNode.props().title).toBe(
      'View 36 Instances with error JSON path $.paid has no result.'
    );

    expect(headerStatistic.props().label).toContain(
      mockIncidentsByError[1].errorMessage
    );
    expect(headerStatistic.props().incidentsCount).toBe(
      mockIncidentsByError[1].instancesWithErrorCount
    );
    // should render a list with 2 items
    expect(contentNode.type()).toBe('ul');
    expect(contentNode.find(Styled.VersionLi).length).toBe(2);

    // should render two statistics
    expect(contentNode.find(IncidentByError).length).toBe(2);
    // should pass the right props to a statistic

    const unitIncidentByErrorNode = contentNode.find(IncidentByError).at(0);

    expect(unitIncidentByErrorNode.props().label).toContain(
      `Version ${mockIncidentsByError[1].workflows[0].version}`
    );
    expect(unitIncidentByErrorNode.props().label).toContain(
      mockIncidentsByError[1].workflows[0].name
    );
    expect(unitIncidentByErrorNode.props().perUnit).toBe(true);
    expect(unitIncidentByErrorNode.props().incidentsCount).toBe(
      mockIncidentsByError[1].workflows[0].instancesWithActiveIncidentsCount
    );
  });
});
