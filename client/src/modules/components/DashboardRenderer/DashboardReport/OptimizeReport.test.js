/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {OptimizeReport} from './OptimizeReport';
import {ReportRenderer} from 'components';

const loadReport = jest.fn().mockReturnValue({id: 'a'});

const props = {
  report: {
    id: 'a',
  },
  filter: [{type: 'runningInstancesOnly', data: null}],
  loadReport,
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('should load the report provided by id', () => {
  shallow(<OptimizeReport {...props} />);

  expect(loadReport).toHaveBeenCalledWith(props.report.id, props.filter, {});
});

it('should render the ReportRenderer if data is loaded', async () => {
  loadReport.mockReturnValue('data');

  const node = shallow(<OptimizeReport {...props} />);

  await node.instance().loadReport();

  expect(node.find(ReportRenderer)).toExist();
});

it('should contain the report name', async () => {
  loadReport.mockReturnValue({name: 'Report Name'});
  const node = shallow(<OptimizeReport {...props} />);

  await node.instance().loadReport();

  expect(node.find('EntityName').children()).toIncludeText('Report Name');
});

it('should provide a link to the report', async () => {
  loadReport.mockReturnValue({name: 'Report Name', id: 'a'});
  const node = shallow(<OptimizeReport {...props} />);

  await node.instance().loadReport();
  node.update();

  expect(node.find('EntityName').children()).toIncludeText('Report Name');
  expect(node.find('EntityName')).toHaveProp('linkTo', 'report/a/');
});

it('should not provide a link to the report when link is disabled', async () => {
  loadReport.mockReturnValue({name: 'Report Name'});
  const node = shallow(<OptimizeReport {...props} disableNameLink />);

  await node.instance().loadReport();
  node.update();

  expect(node.find('EntityName')).toHaveProp('linkTo', false);
  expect(node.find('EntityName').children()).toIncludeText('Report Name');
});

it('should display the name of a failing report', async () => {
  loadReport.mockReturnValue({
    reportDefinition: {name: 'Failing Name'},
  });
  const node = shallow(
    <OptimizeReport {...props} mightFail={(data, success, fail) => fail(data)} disableNameLink />
  );

  await node.instance().loadReport();

  expect(node.find('EntityName').children()).toIncludeText('Failing Name');
});

it('should pass an error message if there is an error and no report is returned', async () => {
  const error = {
    status: 400,
    errorMessage: 'Is failing',
    reportDefinition: null,
  };
  loadReport.mockReturnValueOnce(error);

  const node = await shallow(
    <OptimizeReport {...props} mightFail={(data, success, fail) => fail(data)} disableNameLink />
  );
  await flushPromises();

  expect(node.find(ReportRenderer).prop('error')).toEqual(error);

  node.setProps({mightFail: props.mightFail});
  await node.instance().loadReport();

  expect(node.find(ReportRenderer).prop('error')).toEqual(null);
});

it('should reload the report if the filter changes', async () => {
  const node = shallow(<OptimizeReport {...props} filter={[{type: 'runningInstancesOnly'}]} />);

  await node.instance().loadReport();

  loadReport.mockClear();
  node.setProps({filter: [{type: 'suspendedInstancesOnly', data: null}]});

  expect(loadReport).toHaveBeenCalledWith(
    props.report.id,
    [{type: 'suspendedInstancesOnly', data: null}],
    {}
  );
});
