/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {reportConfig} from 'services';

import Number from './Number';
import ProgressBar from './ProgressBar';

jest.mock('services', () => {
  return {
    formatters: {
      convertDurationToSingleNumber: () => 12,
    },
    reportConfig: {
      process: {
        findSelectedOption: jest.fn().mockReturnValue({key: 'pi_count'}),
        options: {},
      },
    },
  };
});

jest.mock('./ProgressBar', () => () => <div>ProgressBar</div>);

const report = {
  reportType: 'process',
  combined: false,
  data: {
    configuration: {
      targetValue: {active: false},
    },
    view: {
      property: 'frequency',
    },
    visualization: 'Number',
  },
  result: {data: 1234},
};

it('should display the number provided per data property', () => {
  const node = shallow(<Number report={report} formatter={(v) => v} />);
  expect(node).toIncludeText('1234');
});

it('should format data according to the provided formatter', () => {
  const node = shallow(<Number report={report} formatter={(v) => 2 * v} />);

  expect(node).toIncludeText('246');
});

it('should display a progress bar if target values are active', () => {
  const node = shallow(
    <Number
      report={{
        ...report,
        data: {
          ...report.data,
          configuration: {
            targetValue: {active: true, countProgress: {baseline: '0', target: '12'}},
          },
        },
      }}
      formatter={(v) => 2 * v}
    />
  );

  expect(node.find(ProgressBar)).toExist();
});

it('should show the view label underneath the number', () => {
  const node = shallow(<Number report={report} formatter={(v) => v} />);
  expect(node).toIncludeText('Process Instance Count');

  reportConfig.process.findSelectedOption.mockReturnValueOnce({key: 'pi_duration'});
  node.setProps({
    report: {
      reportType: 'process',
      result: {data: 123},
      data: {
        configuration: {aggregationType: 'avg', targetValue: {active: false}},
        view: {
          entity: 'processInstance',
          property: 'duration',
        },
        visualization: 'Number',
      },
    },
  });

  expect(node).toIncludeText('Process Instance Duration - Avg');
});
