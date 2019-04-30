/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import Report from './Report';
import {loadEntity, evaluateReport} from 'services';

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    evaluateReport: jest.fn(),
    loadEntity: jest.fn()
  };
});

const props = {
  match: {params: {id: '1'}},
  location: {}
};

const report = {
  id: 'reportID',
  name: 'name',
  lastModifier: 'lastModifier',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reportType: 'process',
  combined: false,
  data: {
    processDefinitionKey: null,
    configuration: {},
    parameters: {},
    visualization: 'table'
  },
  result: {data: [1, 2, 3]}
};

loadEntity.mockReturnValue(report);
evaluateReport.mockReturnValue(report);

it('should display a loading indicator', () => {
  const node = shallow(<Report {...props} />).dive();

  expect(node.find('LoadingIndicator')).toExist();
});

it("should show an error page if report doesn't exist", () => {
  const node = shallow(<Report {...props} />).dive();
  node.setState({
    serverError: 404
  });

  expect(node.find('ErrorPage')).toExist();
});

it('should initially evaluate the report', () => {
  shallow(<Report {...props} />);

  expect(evaluateReport).toHaveBeenCalled();
});

it('should render ReportEdit component if viewMode is edit', async () => {
  props.match.params.viewMode = 'edit';

  const node = await shallow(<Report {...props} />).dive();
  node.setState({loaded: true, report});

  expect(node.find('ReportEditErrorHandler')).toExist();
});

it('should render ReportView component if viewMode is view', async () => {
  props.match.params.viewMode = 'view';

  const node = await shallow(<Report {...props} />).dive();
  node.setState({loaded: true, report});

  expect(node.find('ReportView')).toExist();
});
