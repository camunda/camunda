/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {BrowserRouter as Router} from 'react-router-dom';
import {shallow, mount} from 'enzyme';

import IncidentsByError from './IncidentsByError';
import InstancesBar from 'modules/components/InstancesBar';
import ExpandButton from 'modules/components/ExpandButton';
import {ThemeProvider} from 'modules/theme';
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
    errorMessage: 'No space left on device.',
    workflows: [
      createWorkflow({name: 'workflowA', version: 42}),
      createWorkflow({name: 'workflowB', version: 23})
    ]
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

  it('should render incident items correctly', () => {
    const node = mount(
      <Router>
        <ThemeProvider>
          <IncidentsByError incidents={mockIncidentsByError} />
        </ThemeProvider>
      </Router>
    );

    const firstIncidentItem = node
      .find('[data-test="incident-byError-0"]')
      .last();
    const firstIncidentLink = firstIncidentItem.find('a').props().href;
    const firstEncodedFilter = encodeURIComponent(
      `{"errorMessage":"JSON path '$.paid' has no result.","incidents":true}`
    );

    const secondIncidentItem = node
      .find('[data-test="incident-byError-1"]')
      .last();
    const secondIncidentLink = secondIncidentItem.find('a').props().href;
    const secondEncodedFilter = encodeURIComponent(
      `{"errorMessage":"No space left on device.","incidents":true}`
    );

    expect(firstIncidentLink).toBe(`/instances?filter=${firstEncodedFilter}`);
    expect(firstIncidentItem.find(InstancesBar).text()).toContain(
      "JSON path '$.paid' has no result."
    );

    expect(secondIncidentLink).toBe(`/instances?filter=${secondEncodedFilter}`);
    expect(secondIncidentItem.find(InstancesBar).text()).toContain(
      'No space left on device.'
    );
  });

  it('should render all versions of incidents correctly', () => {
    const node = mount(
      <Router>
        <ThemeProvider>
          <IncidentsByError incidents={mockIncidentsByError} />
        </ThemeProvider>
      </Router>
    );

    const secondIncidentItem = node
      .find('[data-test="incident-byError-1"]')
      .last();

    secondIncidentItem.find(ExpandButton).simulate('click');

    const expandedVersionLi = node.find(Styled.VersionLiInstancesBar);

    expect(expandedVersionLi.length).toBe(2);

    expect(expandedVersionLi.at(0).text()).toContain('workflowA – Version 42');
    expect(expandedVersionLi.at(1).text()).toContain('workflowB – Version 23');
  });
});
