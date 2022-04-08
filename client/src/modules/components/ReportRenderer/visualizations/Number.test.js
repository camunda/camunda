/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';
import update from 'immutability-helper';
import {loadVariables} from 'services';

import {Number} from './Number';
import ProgressBar from './ProgressBar';

jest.mock('services', () => {
  return {
    loadVariables: jest.fn().mockReturnValue([]),
    formatters: {
      convertDurationToSingleNumber: () => 12,
      frequency: (data) => data,
      duration: (data) => data,
    },
    reportConfig: {
      process: {
        view: [
          {
            matcher: () => true,
            label: () => 'Process Instance',
          },
        ],
      },
    },
  };
});

jest.mock('./ProgressBar', () => () => <div>ProgressBar</div>);

beforeEach(() => {
  loadVariables.mockClear();
});

const report = {
  reportType: 'process',
  combined: false,
  data: {
    configuration: {
      targetValue: {active: false},
    },
    view: {
      properties: ['frequency'],
    },
    visualization: 'Number',
  },
  result: {measures: [{property: 'frequency', data: 1234}]},
};

const variable = {name: 'foo', type: 'String'};
const variableReport = update(report, {
  data: {view: {$set: {entity: 'variable', properties: [variable]}}},
  result: {
    measures: {
      $set: [{data: 123, aggregationType: {type: 'avg', value: null}, property: variable}],
    },
  },
});

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('should display the number provided per data property', () => {
  const node = shallow(<Number report={report} />);
  expect(node).toIncludeText('1234');
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

it('should not display a progress bar for multi measure/aggregation reports', () => {
  const node = shallow(
    <Number
      report={{
        ...report,
        data: {
          ...report.data,
          view: {
            properties: ['duration'],
          },
          configuration: {
            aggregationTypes: ['avg', 'max'],
            targetValue: {active: true, countProgress: {baseline: '0', target: '12'}},
          },
        },
      }}
      formatter={(v) => 2 * v}
    />
  );

  expect(node.find(ProgressBar)).not.toExist();
});

it('should show the view label underneath the number', () => {
  const node = shallow(<Number report={report} />);
  expect(node).toIncludeText('Process Instance Count');

  node.setProps({
    report: {
      reportType: 'process',
      result: {
        measures: [{data: 123, aggregationType: {type: 'avg', value: null}, property: 'duration'}],
      },
      data: {
        configuration: {targetValue: {active: false}},
        view: {
          entity: 'processInstance',
          properties: ['duration'],
        },
        visualization: 'Number',
      },
    },
  });

  expect(node).toIncludeText('Process Instance Duration - Avg');
});

it('should show multiple measures', () => {
  const node = shallow(
    <Number
      report={update(report, {
        data: {view: {properties: {$set: ['frequency', 'duration']}}},
        result: {
          measures: {
            $set: [
              {data: 26, property: 'frequency'},
              {data: 618294147, propery: 'duration'},
            ],
          },
        },
      })}
    />
  );

  expect(node.find('.data').length).toBe(2);
  expect(node.find('.label').length).toBe(2);
});

it('should show the variable name', () => {
  const node = shallow(<Number report={variableReport} {...props} />);

  runLastEffect();

  expect(node).toIncludeText('foo - Avg');
});

it('should show the variable label if it exists', () => {
  loadVariables.mockReturnValueOnce([{...variable, label: 'FooLabel'}]);
  const node = shallow(<Number report={variableReport} {...props} />);

  runLastEffect();

  expect(node).toIncludeText('FooLabel - Avg');
});
