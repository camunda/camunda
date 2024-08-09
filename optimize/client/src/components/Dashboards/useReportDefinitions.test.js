/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {loadEntity} from 'services';

import useReportDefinitions from './useReportDefinitions';

jest.mock('services', () => ({
  loadEntity: jest.fn().mockResolvedValue({data: {definitions: [{key: 'DefKey'}]}}),
}));

jest.mock('hooks', () => ({
  useErrorHandling: jest.fn().mockImplementation(() => ({
    mightFail: jest.fn().mockImplementation(async (data, cb, err) => {
      try {
        const awaitedData = await data;
        return cb(awaitedData);
      } catch (e) {
        err?.(e);
      }
    }),
  })),
}));

function Mock({existingReport, errorHandler}) {
  const {definitions} = useReportDefinitions(existingReport, errorHandler);

  return <span>{JSON.stringify(definitions)}</span>;
}

it('should fetch the report and return definitions if report id is passed', async () => {
  const existingReport = {
    id: 'reportId',
  };
  const node = shallow(<Mock existingReport={existingReport} />);

  runLastEffect();
  await flushPromises();

  expect(loadEntity).toHaveBeenCalledWith('report', 'reportId');
  expect(node.find('span').text()).toEqual(JSON.stringify([{key: 'DefKey'}]));
});

it('should return definitions from report data if no id specified', async () => {
  const existingReport = {data: {definitions: [{key: 'Some Key'}]}};
  const node = shallow(<Mock existingReport={existingReport} />);

  runLastEffect();

  expect(loadEntity).not.toHaveBeenCalled();
  expect(node.find('span').text()).toEqual(JSON.stringify([{key: 'Some Key'}]));

  node.setProps({existingReport: {data: {definitions: [{key: 'Some Other Key'}]}}});
  runLastEffect();

  expect(loadEntity).not.toHaveBeenCalled();
  expect(node.find('span').text()).toEqual(JSON.stringify([{key: 'Some Other Key'}]));
});

it('should return empty array if existing report misses id and definitions', async () => {
  const existingReport = {data: {something: 'something'}};
  const node = shallow(<Mock existingReport={existingReport} />);

  runLastEffect();
  await flushPromises();

  expect(loadEntity).not.toHaveBeenCalled();
  expect(node.find('span').text()).toEqual(JSON.stringify([]));
});

it('should handle error', async () => {
  const error = new Error();
  loadEntity.mockRejectedValueOnce(error);
  const existingReport = {id: 'reportId'};
  const errorHandler = jest.fn();
  const node = shallow(<Mock existingReport={existingReport} errorHandler={errorHandler} />);

  runLastEffect();
  await flushPromises();

  expect(loadEntity).toHaveBeenCalled();
  expect(errorHandler).toHaveBeenCalledWith(error);
  expect(node.find('span').text()).toEqual('');
});
