/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {mount} from 'enzyme';

import DashboardView from './DashboardView';

jest.mock('./DashboardReport', () => {
  return {DashboardReport: ({report}) => <div>Report {report.id}</div>};
});
jest.mock('./DashboardObject', () => {
  return {DashboardObject: ({children}) => <div>{children}</div>};
});

const reports = [
  {
    position: {x: 0, y: 0},
    dimensions: {width: 3, height: 1},
    id: '1'
  },
  {
    position: {x: 2, y: 0},
    dimensions: {width: 1, height: 4},
    id: '2'
  },
  {
    position: {x: 3, y: 1},
    dimensions: {width: 2, height: 2},
    id: '3'
  }
];

it('should render a Dashboard Report for every Report in the props', () => {
  const node = mount(<DashboardView reports={reports} />);

  expect(node).toIncludeText('Report 1');
  expect(node).toIncludeText('Report 2');
  expect(node).toIncludeText('Report 3');
});

it('should render additional child components', () => {
  const AdditionalContent = () => <div>Additional Content</div>;

  const node = mount(
    <DashboardView reports={reports}>
      <AdditionalContent />
    </DashboardView>
  );

  expect(node).toIncludeText('Additional Content');
});

it('should provide child components with the container, tileDimensions and reports', () => {
  const PropertyPrinter = props => <div>{Object.keys(props)}</div>;

  const node = mount(
    <DashboardView reports={reports}>
      <PropertyPrinter />
    </DashboardView>
  );

  expect(node).toIncludeText('container');
  expect(node).toIncludeText('tileDimensions');
  expect(node).toIncludeText('reports');
});
