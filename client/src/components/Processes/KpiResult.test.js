/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {isSuccessful} from './service';
import KpiResult from './KpiResult';

jest.mock('./service', () => ({isSuccessful: jest.fn()}));
jest.mock('services', () => ({
  ...jest.requireActual('services'),
  formatters: {
    duration: (value) => value + 'ms',
    percentage: (value) => value + '%',
    frequency: (value) => value,
  },
}));

const kpis = [
  {
    reportName: 'report Name',
    value: '123',
    target: '300',
    isBelow: true,
    measure: 'frequency',
  },
];

it('should display NoDataNotice if kpis are empty or have null values', () => {
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

it('should add "success" className to the report value if the kpi is successful', () => {
  isSuccessful.mockReturnValueOnce(true);
  const node = shallow(<KpiResult kpis={kpis} />);

  expect(node.find('.reportValue')).toHaveClassName('success');
});

it('should display kpi value and target in correct format', () => {
  const node = shallow(
    <KpiResult
      kpis={[
        {
          reportName: 'report Name',
          value: '123',
          target: '300',
          unit: null,
          isBelow: true,
          measure: 'percentage',
        },
      ]}
    />
  );

  expect(node.find('.reportValues span').at(0)).toIncludeText('123%');
  expect(node.find('.reportValues span').at(1)).toIncludeText('300%');

  node.setProps({
    kpis: [
      {
        reportName: 'report Name',
        value: '12',
        target: '4',
        unit: 'days',
        isBelow: true,
        measure: 'duration',
      },
    ],
  });

  expect(node.find('.reportValues span').at(0)).toIncludeText('12ms');
  expect(node.find('.reportValues span').at(1)).toIncludeText('4 days');
});

it('should add collection part to the link if kpi has collectionId', () => {
  const node = shallow(
    <KpiResult
      kpis={[
        {
          reportName: 'report Name',
          value: '123',
          target: '300',
          unit: null,
          isBelow: true,
          measure: 'percentage',
          collectionId: 'collectionId',
          reportId: 'reportId',
        },
      ]}
    />
  );

  expect(node.find('Link').prop('to')).toBe('/collection/collectionId/report/reportId/');
});
