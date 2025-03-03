/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {runAllEffects, runLastEffect} from 'react';
import {shallow} from 'enzyme';
import {DataTableSkeleton} from '@carbon/react';

import {ReportRenderer} from 'components';
import {evaluateReport} from 'services';
import {useErrorHandling} from 'hooks';

import InstanceViewTable from './InstanceViewTable';

const props = {
  report: {data: {configuration: {xml: 'xml data'}}},
};

jest.mock('config', () => ({newReport: {new: {data: {configuration: {}}}}}));

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  evaluateReport: jest.fn().mockReturnValue({}),
}));

jest.mock('hooks', () => ({
  useChangedState: jest.requireActual('react-18').useState,
  useErrorHandling: jest.fn(() => ({
    mightFail: jest.fn((data, cb) => cb(data)),
  })),
}));

it('should display a table skeleton while loading the data', () => {
  const node = shallow(<InstanceViewTable {...props} />);

  expect(node.find(DataTableSkeleton)).toExist();
  expect(node.find(ReportRenderer)).not.toExist();
});

it('should display the ReportRenderer when the data is loaded', () => {
  const node = shallow(<InstanceViewTable {...props} />);
  runAllEffects();
  runAllEffects();
  expect(node.find(ReportRenderer)).toExist();
});

it('should evaluate the raw data of the report on mount', () => {
  shallow(<InstanceViewTable {...props} />);
  runAllEffects();
  expect(evaluateReport).toHaveBeenCalledWith(
    {
      data: {
        configuration: {
          xml: 'xml data',
          sorting: {by: 'startDate', order: 'desc'},
        },
        groupBy: {type: 'none', value: null},
        view: {entity: null, properties: ['rawData']},
        visualization: 'table',
      },
    },
    [],
    undefined
  );
});

it('should evaluate the raw data of the report on report prop change', () => {
  const node = shallow(<InstanceViewTable {...props} />);
  runAllEffects();
  expect(evaluateReport).toHaveBeenCalledWith(
    {
      data: {
        configuration: {
          xml: 'xml data',
          sorting: {by: 'startDate', order: 'desc'},
        },
        groupBy: {type: 'none', value: null},
        view: {entity: null, properties: ['rawData']},
        visualization: 'table',
      },
    },
    [],
    undefined
  );

  node.setProps({report: {data: {configuration: {xml: 'new xml data'}}}});
  runAllEffects();
  evaluateReport.mockClear();
  runLastEffect();
  expect(evaluateReport).toHaveBeenCalledWith(
    {
      data: {
        configuration: {
          xml: 'new xml data',
          sorting: {by: 'startDate', order: 'desc'},
        },
        groupBy: {type: 'none', value: null},
        view: {entity: null, properties: ['rawData']},
        visualization: 'table',
      },
    },
    [],
    undefined
  );
});

it('should pass the error to reportRenderer if evaluation fails', async () => {
  const testError = {message: 'testError', reportDefinition: {}, status: 400};
  useErrorHandling.mockImplementationOnce(() => ({
    mightFail: (_promise, _cb, err) => err(testError),
  }));

  const node = shallow(<InstanceViewTable {...props} />);
  runAllEffects();
  await flushPromises();

  expect(node.find(ReportRenderer).prop('error')).toEqual({status: 400, ...testError});
});

it('evaluate re-evaluate the report when called loadReport prop', () => {
  const node = shallow(<InstanceViewTable {...props} />);
  runAllEffects();

  const sortParams = {limit: '20', offset: 0};
  const report = {data: {configuration: {sorting: {by: 'startDate', order: 'asc'}}}};
  node.find(ReportRenderer).prop('loadReport')(sortParams, report);

  evaluateReport.mockClear();
  runAllEffects();

  expect(evaluateReport).toHaveBeenCalledWith(report, [], sortParams);
});
