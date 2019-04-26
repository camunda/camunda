/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ThemedOptimizeReport from './OptimizeReport';

const {WrappedComponent: OptimizeReportWithErrorHandling} = ThemedOptimizeReport;
const {WrappedComponent: OptimizeReport} = OptimizeReportWithErrorHandling;

const loadReport = jest.fn();

const props = {
  report: {
    id: 'a'
  },
  loadReport,
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data))
};

it('should load the report provided by id', () => {
  shallow(<OptimizeReport {...props} />);

  expect(loadReport).toHaveBeenCalledWith(props.report.id);
});

it('should render the ReportRenderer if data is loaded', async () => {
  loadReport.mockReturnValue('data');

  const node = shallow(<OptimizeReport {...props} />);

  await node.instance().loadReport();

  expect(node).toIncludeText('ReportRenderer');
});

it('should contain the report name', async () => {
  loadReport.mockReturnValue({name: 'Report Name'});
  const node = shallow(<OptimizeReport {...props} />);

  await node.instance().loadReport();

  expect(node.find('Link').children()).toIncludeText('Report Name');
});

it('should provide a link to the report', async () => {
  loadReport.mockReturnValue({name: 'Report Name'});
  const node = shallow(<OptimizeReport {...props} />);

  await node.instance().loadReport();
  node.update();

  expect(node.find('Link').children()).toIncludeText('Report Name');
  expect(node.find('Link')).toHaveProp('to', '/report/a');
});

it('should not provide a link to the report when link is disabled', async () => {
  loadReport.mockReturnValue({name: 'Report Name'});
  const node = shallow(<OptimizeReport {...props} disableNameLink />);

  await node.instance().loadReport();
  node.update();

  expect(node.find('a')).not.toBePresent();
  expect(node).toIncludeText('Report Name');
});

it('should display the name of a failing report and the error message', async () => {
  loadReport.mockReturnValue({
    json: () => ({
      errorMessage: 'Is failing',
      reportDefinition: {name: 'Failing Name'}
    })
  });
  const node = shallow(
    <OptimizeReport {...props} mightFail={(data, success, fail) => fail(data)} disableNameLink />
  );

  await node.instance().loadReport();

  expect(node).toIncludeText('Failing Name');
  expect(node.find('NoDataNotice').prop('children')).toBe('Is failing');
});
