/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ReportDetails from './ReportDetails';
import SingleReportDetails from './SingleReportDetails';

const props = {
  report: {
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
