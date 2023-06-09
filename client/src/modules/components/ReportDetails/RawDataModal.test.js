/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {ReportRenderer, Modal} from 'components';
import {evaluateReport} from 'services';

import {RawDataModal} from './RawDataModal';

const props = {
  name: 'processName',
  open: true,
  report: {data: {configuration: {xml: 'xml data'}}},
  onClose: jest.fn(),
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

jest.mock('config', () => ({newReport: {new: {data: {configuration: {}}}}}));

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  evaluateReport: jest.fn().mockReturnValue({}),
}));

it('should contain ReportRenderer', () => {
  const node = shallow(<RawDataModal {...props} />);
  runLastEffect();
  expect(node.find(Modal)).toExist();
  expect(node.find(ReportRenderer)).toExist();
});

it('evaluate the raw data of the report on mount', () => {
  shallow(<RawDataModal {...props} />);
  runLastEffect();
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

it('should pass the error to reportRenderer if evaluation fails', async () => {
  const testError = {message: 'testError', reportDefinition: {}, status: 400};
  const mightFail = (promise, cb, err) => err(testError);

  const node = shallow(<RawDataModal {...props} mightFail={mightFail} />);
  runLastEffect();
  await flushPromises();

  expect(node.find(ReportRenderer).prop('error')).toEqual({status: 400, ...testError});
});

it('evaluate re-evaluate the report when called loadReport prop', () => {
  const node = shallow(<RawDataModal {...props} />);
  runLastEffect();

  const sortParams = {limit: '20', offset: 0};
  const report = {data: {configuration: {sorting: {by: 'startDate', order: 'asc'}}}};
  node.find(ReportRenderer).prop('loadReport')(sortParams, report);

  evaluateReport.mockClear();
  runLastEffect();

  expect(evaluateReport).toHaveBeenCalledWith(report, [], sortParams);
});
