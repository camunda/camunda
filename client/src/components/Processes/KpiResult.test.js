/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import KpiResult from './KpiResult';

const kpis = [
  {
    reportName: 'report Name',
    value: '123',
    target: '300',
    isBelow: true,
    measure: 'frequency',
  },
];

it('should display NoDataNotice if kpis is empty or has null values', () => {
  const node = shallow(<KpiResult kpis={kpis} />);

  expect(node.find('.kpi')).toExist();
  expect(node.find('NoDataNotice')).not.toExist();

  node.setProps({
    kpis: [],
  });

  expect(node.find('.kpi')).not.toExist();
  expect(node.find('NoDataNotice')).toExist();

  node.setProps({
    kpis: [
      {
        reportName: 'report Name',
        value: null,
        target: '300',
        isBelow: true,
        measure: 'frequency',
      },
    ],
  });

  expect(node.find('.kpi')).not.toExist();
  expect(node.find('NoDataNotice')).toExist();
});
