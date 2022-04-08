/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {processResult} from 'services';

import ProcessReportRenderer from './ProcessReportRenderer';
import {Number, Table} from './visualizations';

jest.mock('./service', () => {
  return {
    getFormatter: (view) => (v) => v,
  };
});

jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    processResult: jest.fn().mockImplementation(({result}) => result),
  };
});

const report = {
  combined: false,
  reportType: 'process',
  data: {
    processDefinitionKey: 'aKey',
    processDefinitionVersion: '1',
    view: {
      properties: ['foo'],
      entity: 'whatever',
    },
    groupBy: {
      type: 'bar',
    },
    visualization: 'number',
    configuration: {},
  },
  result: {data: 1234},
};

it('should display a number if visualization is number', () => {
  const node = shallow(<ProcessReportRenderer report={report} />);
  node.setState({
    loaded: true,
  });

  expect(node.find(Number)).toExist();
  expect(node.find(Number).prop('report')).toEqual(report);
});

it('should provide an errorMessage property to the component', () => {
  const node = shallow(<ProcessReportRenderer report={report} errorMessage={'test'} />);
  node.setState({
    loaded: true,
  });
  expect(node.find(Number)).toHaveProp('errorMessage');
});

const exampleDurationReport = {
  combined: false,
  reportType: 'process',
  data: {
    processDefinitionKey: 'aKey',
    processDefinitionVersion: '1',
    view: {
      properties: ['foo'],
      entity: 'whatever',
    },
    groupBy: {
      type: 'processInstance',
      unit: 'day',
    },
    visualization: 'table',
    configuration: {},
  },
  result: {
    data: {'2015-03-25T12:00:00Z': 2, '2015-03-26T12:00:00Z': 3},
  },
};

it('should pass the report to the visualization component', () => {
  const node = shallow(<ProcessReportRenderer report={exampleDurationReport} type="process" />);
  node.setState({
    loaded: true,
  });

  expect(node.find(Table)).toHaveProp('report', exampleDurationReport);
});

it('should process the report result', () => {
  shallow(<ProcessReportRenderer report={exampleDurationReport} />);

  expect(processResult).toHaveBeenCalledWith(exampleDurationReport);
});
