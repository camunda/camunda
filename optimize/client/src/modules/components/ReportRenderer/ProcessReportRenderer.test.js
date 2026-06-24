/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {processResult} from 'services';

import ProcessReportRenderer from './ProcessReportRenderer';
import {Number, Table} from './visualizations';

jest.mock('./service', () => {
  return {
    getFormatter: () => (v) => v,
  };
});

jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    processResult: jest.fn().mockImplementation(({result}) => result),
  };
});

const report = {
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

  expect(node.find(Number)).toExist();
  expect(node.find(Number).prop('report')).toEqual(report);
});

it('should provide an errorMessage property to the component', () => {
  const node = shallow(<ProcessReportRenderer report={report} errorMessage={'test'} />);

  expect(node.find(Number)).toHaveProp('errorMessage');
});

const exampleDurationReport = {
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

  expect(node.find(Table)).toHaveProp('report', exampleDurationReport);
});

it('should process the report result', () => {
  shallow(<ProcessReportRenderer report={exampleDurationReport} />);

  expect(processResult).toHaveBeenCalledWith(exampleDurationReport);
});
