/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runLastEffect} from 'react';
import {shallow} from 'enzyme';
import {useLocation, useParams} from 'react-router';

import {ReportRenderer, InstanceCount} from 'components';
import {useErrorHandling} from 'hooks';

import {Sharing} from './Sharing';
import {evaluateEntity} from './service';

jest.mock('react-router', () => ({
  ...jest.requireActual('react-router'),
  useParams: jest.fn().mockReturnValue({type: 'report', id: 123}),
  useLocation: jest.fn().mockReturnValue({search: ''}),
}));

jest.mock('./service', () => {
  return {
    evaluateEntity: jest.fn(),
    createLoadReportCallback: jest.fn(),
  };
});

jest.mock('hooks', () => ({
  useErrorHandling: jest.fn(() => ({
    mightFail: jest.fn().mockImplementation((data, cb, err, final) => {
      try {
        cb(data);
      } catch (e) {
        err(e);
      }
      final();
    }),
  })),
}));

it('should render without crashing', () => {
  shallow(<Sharing />);
});

it('should initially load data', () => {
  shallow(<Sharing />);

  runLastEffect();

  expect(evaluateEntity).toHaveBeenCalled();
});

it('should display a loading indicator', () => {
  const node = shallow(<Sharing mightFail={() => {}} />);

  expect(node.find('Loading')).toExist();
});

it('should display an error message if evaluation was unsuccessful', () => {
  const node = shallow(<Sharing />);

  evaluateEntity.mockReturnValueOnce(null);

  runLastEffect();

  expect(node.find('ErrorPage')).toExist();
});

it('should pass the error to reportRenderer if evaluation fails', async () => {
  useParams.mockReturnValueOnce({type: 'report'});
  const testError = {status: 400, message: 'testError', reportDefinition: {}};
  const mightFail = (_promise, _cb, err, final) => {
    err(testError);
    final();
  };

  useErrorHandling.mockImplementationOnce(() => ({mightFail}));

  const node = await shallow(<Sharing />);

  await flushPromises();
  runLastEffect();

  expect(node.find(ReportRenderer).prop('error')).toEqual(testError);
});

it('should display an error message if type is invalid', async () => {
  useParams.mockReturnValue({type: 'foo'});
  const node = shallow(<Sharing />);

  await flushPromises();
  runLastEffect();

  expect(node.find('ErrorPage')).toExist();
});

it('should have report if everything is fine', () => {
  useParams.mockReturnValue({type: 'report'});
  const node = shallow(<Sharing />);

  evaluateEntity.mockReturnValueOnce({name: 'foo'});

  runLastEffect();

  expect(node.find(ReportRenderer)).toExist();
});

it('should retrieve report for the given id', () => {
  useParams.mockReturnValue({type: 'report', id: 123});
  shallow(<Sharing />);

  runLastEffect();

  expect(evaluateEntity).toHaveBeenCalledWith(123, 'report', undefined);
});

it('should display the report name and include report details', () => {
  useParams.mockReturnValueOnce({type: 'report'});
  const node = shallow(<Sharing />);

  evaluateEntity.mockReturnValueOnce({name: 'My report name'});

  runLastEffect();

  expect(node.find('EntityName')).toExist();
  expect(node.find('EntityName').prop('name')).toBe('My report name');
  expect(node.find('EntityName').prop('details').props.report).toEqual({name: 'My report name'});
});

it('should include the InstanceCount for reports', () => {
  useParams.mockReturnValueOnce({type: 'report'});
  const node = shallow(<Sharing />);

  evaluateEntity.mockReturnValueOnce({name: 'My report name'});

  runLastEffect();

  expect(node.find(InstanceCount)).toExist();
  expect(node.find(InstanceCount).prop('report')).toEqual({name: 'My report name'});
});

it('should have dashboard if everything is fine', () => {
  useParams.mockReturnValue({type: 'dashboard', id: 123});
  const node = shallow(<Sharing />);

  evaluateEntity.mockReturnValueOnce({reportShares: 'foo'});

  runLastEffect();

  expect(node.find('DashboardRenderer')).toExist();
});

it('should include filters on a dashboard', () => {
  useParams.mockReturnValue({type: 'dashboard', id: 123});
  useLocation.mockReturnValue({
    search: '?filter=%5B%7B%22type%22%3A%22runningInstancesOnly%22%2C%22data%22%3Anull%7D%5D',
  });
  const node = shallow(<Sharing />);

  evaluateEntity.mockReturnValueOnce({reportShares: 'foo'});

  runLastEffect();

  expect(node.find('DashboardRenderer').prop('filter')).toEqual([
    {data: null, type: 'runningInstancesOnly'},
  ]);
});

it('should retrieve dashboard for the given id', () => {
  useParams.mockReturnValue({type: 'dashboard', id: 123});
  shallow(<Sharing />);

  runLastEffect();

  expect(evaluateEntity).toHaveBeenCalledWith(123, 'dashboard', undefined);
});

it('should display the dashboard name and last modification info', () => {
  useParams.mockReturnValue({type: 'dashboard', id: 123});
  const node = shallow(<Sharing />);

  evaluateEntity.mockReturnValueOnce({name: 'My dashboard name'});

  runLastEffect();

  expect(node.find('EntityName')).toExist();
  expect(node.find('EntityName').prop('name')).toBe('My dashboard name');
  expect(node.find('EntityName').prop('details').props.entity).toEqual({name: 'My dashboard name'});
});

it('should render an href directing to view mode ignoring /external sub url', () => {
  useParams.mockReturnValue({type: 'dashboard', id: 123});
  delete window.location;
  window.location = new URL('http://example.com/subUrl/external/#/share/dashboard/shareId');

  const node = shallow(<Sharing />);

  evaluateEntity.mockReturnValueOnce({id: 'dashboardId'});

  runLastEffect();

  expect(node.find('Link').prop('href')).toBe('http://example.com/subUrl/#/dashboard/dashboardId/');
});

it('should show a compact version and lock scroll when a shared report is embedded', () => {
  useParams.mockReturnValue({type: 'report', id: 123});
  useLocation.mockReturnValue({search: '?mode=embed'});

  const node = shallow(<Sharing />);

  evaluateEntity.mockReturnValueOnce({name: 'My report name'});

  runLastEffect();

  expect(node.find('DiagramScrollLock')).toExist();
  expect(node.find('.iconLink')).toExist();
  expect(node.find('.Sharing')).toHaveClassName('compact');
});

it('should add report classname to the sharing container', () => {
  useParams.mockReturnValue({type: 'report', id: 123});
  useLocation.mockReturnValue({search: '?header=hidden'});

  const node = shallow(<Sharing />);

  evaluateEntity.mockReturnValueOnce({name: 'My report name', id: 'aReportId'});

  runLastEffect();

  expect(node.find('.Sharing')).toHaveClassName('report');
});

it('should only show the title and hide the link to Optimize', () => {
  useLocation.mockReturnValue({search: '?header=titleOnly'});
  evaluateEntity.mockReturnValueOnce({name: 'My report name', id: 'aReportId'});

  const node = shallow(<Sharing />);

  runLastEffect();

  expect(node.find(InstanceCount)).toExist();
  expect(node.find('EntityName')).toExist();
  expect(node.find('.title-button')).not.toExist();
});

it('should only show the link to Optimize and hide the title', () => {
  useLocation.mockReturnValue({search: '?header=linkOnly'});
  evaluateEntity.mockReturnValueOnce({name: 'My report name', id: 'aReportId'});

  const node = shallow(<Sharing />);

  runLastEffect();

  expect(node.find('.title-button')).toExist();
  expect(node.find('EntityName')).not.toExist();
});

it('should hide the whole header if specified', () => {
  useParams.mockReturnValueOnce({type: 'report', id: 123});
  useLocation.mockReturnValueOnce({search: '?header=hidden'});
  evaluateEntity.mockReturnValueOnce({name: 'My report name', id: 'aReportId'});

  const node = shallow(<Sharing />);

  expect(node.find('.cds--header')).not.toExist();
});
