/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';
import update from 'immutability-helper';
import {loadVariables} from 'services';

import {Number} from './Number';
import ProgressBar from './ProgressBar';

jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    loadVariables: jest.fn().mockReturnValue([]),
    formatters: {
      convertDurationToSingleNumber: () => 12,
      frequency: (data) => data,
      duration: (data) => data,
      compact: (v) => (v == null ? '--' : `${v}K`),
    },
    reportConfig: {
      view: [
        {
          matcher: () => true,
          label: () => 'Process instance',
        },
      ],
    },
  };
});

jest.mock('./ProgressBar', () => () => <div>ProgressBar</div>);

beforeEach(() => {
  loadVariables.mockClear();
});

const report = {
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
  data: {
    view: {$set: {entity: 'variable', properties: [variable]}},
    filter: {$set: [{type: 'runningInstancesOnly'}]},
    definitions: {$set: [{key: 'aKey', versions: ['1'], tenantIds: ['tenantId']}]},
  },
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
          configuration: {
            aggregationTypes: ['avg', 'max'],
            targetValue: {active: true, countProgress: {baseline: '0', target: '12'}},
          },
        },
        result: {measures: [{}, {}]},
      }}
      formatter={(v) => 2 * v}
    />
  );

  expect(node.find(ProgressBar)).not.toExist();
});

it('should show the view label underneath the number', () => {
  const node = shallow(<Number report={report} />);
  expect(node).toIncludeText('Process instance Count');

  node.setProps({
    report: {
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

  expect(node).toIncludeText('Process instance Duration - Avg');
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

it('should override the subtitle with configuration.subtitle when set', () => {
  const node = shallow(
    <Number
      report={update(report, {
        data: {configuration: {subtitle: {$set: 'My custom subtitle'}}},
      })}
    />
  );

  // the override is rendered outside the fitted container so it does not shrink the number
  expect(node.find('.container .label').exists()).toBe(false);
  expect(node.find('.subtitle').text()).toBe('My custom subtitle');
  expect(node).not.toIncludeText('Process instance Count');
});

it('should not apply the subtitle override for multi-measure reports', () => {
  const node = shallow(
    <Number
      report={update(report, {
        data: {
          configuration: {subtitle: {$set: 'My custom subtitle'}},
          view: {properties: {$set: ['frequency', 'duration']}},
        },
        result: {
          measures: {
            $set: [
              {data: 26, property: 'frequency'},
              {data: 42, property: 'frequency'},
            ],
          },
        },
      })}
    />
  );

  expect(node).not.toIncludeText('My custom subtitle');
});

it('should call loadVariables for process variable report', () => {
  shallow(<Number report={variableReport} {...props} />);

  runLastEffect();

  expect(loadVariables).toHaveBeenCalledWith({
    filter: [{type: 'runningInstancesOnly'}],
    processesToQuery: [
      {processDefinitionKey: 'aKey', processDefinitionVersions: ['1'], tenantIds: ['tenantId']},
    ],
  });
});

it('should apply valueFormat from configuration instead of measure property', () => {
  const node = shallow(
    <Number
      report={{
        ...report,
        data: {
          ...report.data,
          configuration: {targetValue: {active: false}, valueFormat: 'compact'},
        },
        result: {measures: [{property: 'totalTokens', data: 150000}]},
      }}
    />
  );

  expect(node.find('.data')).toIncludeText('150000K');
});

it('should fall back to measure property formatter when valueFormat is not set', () => {
  const node = shallow(
    <Number
      report={{
        ...report,
        result: {measures: [{property: 'frequency', data: 42}]},
      }}
    />
  );

  expect(node.find('.data')).toIncludeText('42');
});

describe('overlay prop', () => {
  const overlay = <span className="testBadge">delta</span>;

  it('should render overlay inside the first .data element', () => {
    const node = shallow(<Number report={report} overlay={overlay} />);

    expect(node.find('.data').first().find('.testBadge')).toExist();
  });

  it('should not render overlay in subsequent .data elements for multi-measure reports', () => {
    const multiMeasureReport = {
      ...report,
      data: {
        ...report.data,
        view: {properties: ['frequency', 'duration']},
      },
      result: {
        measures: [
          {data: 10, property: 'frequency'},
          {data: 500, property: 'duration', aggregationType: {type: 'avg', value: null}},
        ],
      },
    };
    const node = shallow(<Number report={multiMeasureReport} overlay={overlay} />);

    expect(node.find('.data').at(0).find('.testBadge')).toExist();
    expect(node.find('.data').at(1).find('.testBadge')).not.toExist();
  });

  it('should render nothing in .data when overlay is not provided', () => {
    const node = shallow(<Number report={report} />);

    expect(node.find('.data').first().find('.testBadge')).not.toExist();
  });
});
