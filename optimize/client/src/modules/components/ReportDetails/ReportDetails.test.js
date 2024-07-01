/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import update from 'immutability-helper';

import ReportDetails from './ReportDetails';
import SingleReportDetails from './SingleReportDetails';

const props = {
  report: {
    combined: false,
    owner: 'Test Person',
    lastModified: '2020-06-23T09:32:48.938+0200',
    lastModifier: 'Test Person',
  },
};

it('should show owner and last modified information', () => {
  const node = shallow(<ReportDetails {...props} />);

  expect(node).toIncludeText('Test Person');
  expect(node).toIncludeText('Jun 23');
});

it('should show details for a single report', () => {
  const node = shallow(<ReportDetails {...props} />);

  expect(node.find(SingleReportDetails)).toExist();
  expect(node.find(SingleReportDetails).prop('report')).toBe(props.report);
});

it('show details for each report in a combined report', () => {
  const combinedReport = update(props.report, {
    combined: {$set: true},
    result: {$set: {data: {a: {id: 'a'}, b: {id: 'b'}}}},
  });
  const node = shallow(<ReportDetails report={combinedReport} />);

  const reportNodes = node.find(SingleReportDetails);

  expect(reportNodes).toHaveLength(2);
  expect(reportNodes.first().prop('showReportName')).toBe(true);
  expect(reportNodes.first().prop('report')).toEqual({id: 'a'});
});
