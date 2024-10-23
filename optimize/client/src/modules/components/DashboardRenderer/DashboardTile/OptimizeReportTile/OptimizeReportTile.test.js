/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from 'react';
import {shallow} from 'enzyme';
import {useHistory} from 'react-router-dom';

import {ReportRenderer} from 'components';
import {useErrorHandling} from 'hooks';

import OptimizeReportTile from './OptimizeReportTile';

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useHistory: jest.fn(() => ({push: jest.fn()})),
}));

jest.mock('hooks', () => ({
  useErrorHandling: jest.fn(() => ({
    mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  })),
}));

const loadTile = jest.fn().mockReturnValue({id: 'a'});

const props = {
  tile: {
    type: 'optimize_report',
    id: 'a',
  },
  filter: [{type: 'runningInstancesOnly', data: null}],
  loadTile,
};

beforeEach(() => {
  jest.clearAllMocks();
});

it('should load the report provided by id', () => {
  shallow(<OptimizeReportTile {...props} />);

  runAllEffects();

  expect(loadTile).toHaveBeenCalledWith(props.tile.id, props.filter, {});
});

it('should render the ReportRenderer if data is loaded', async () => {
  loadTile.mockReturnValueOnce('data');

  const node = shallow(<OptimizeReportTile {...props} />);

  runAllEffects();
  await flushPromises();

  expect(node.find(ReportRenderer)).toExist();
});

it('should contain the report name', async () => {
  loadTile.mockReturnValueOnce({name: 'Report Name'});
  const node = shallow(<OptimizeReportTile {...props} />);

  runAllEffects();
  await flushPromises();

  expect(node.find('EntityName').prop('name')).toBe('Report Name');
});

it('should provide a link to the report', async () => {
  loadTile.mockReturnValueOnce({name: 'Report Name', id: 'a'});
  const node = shallow(<OptimizeReportTile {...props} />);

  runAllEffects();
  await flushPromises();

  expect(node.find('EntityName').prop('name')).toBe('Report Name');
  expect(node.find('EntityName')).toHaveProp('linkTo', 'report/a/');
});

it('should use the customizeTileLink prop to get the link if specified', async () => {
  loadTile.mockReturnValueOnce({name: 'Report Name', id: 'a'});
  const node = shallow(<OptimizeReportTile {...props} customizeTileLink={(id) => `test/${id}/`} />);

  runAllEffects();
  await flushPromises();

  expect(node.find('EntityName')).toHaveProp('linkTo', 'test/a/');
});

it('should not provide a link to the report when link is disabled', async () => {
  loadTile.mockReturnValueOnce({name: 'Report Name'});
  const node = shallow(<OptimizeReportTile {...props} disableNameLink />);

  runAllEffects();
  await flushPromises();

  expect(node.find('EntityName')).toHaveProp('linkTo', false);
  expect(node.find('EntityName').prop('name')).toBe('Report Name');
});

it('should display the name of a failing report', async () => {
  loadTile.mockReturnValueOnce({
    reportDefinition: {name: 'Failing Name'},
  });

  useErrorHandling.mockReturnValueOnce({
    mightFail: (data, _success, fail) => fail(data),
  });

  const node = shallow(<OptimizeReportTile {...props} disableNameLink />);

  runAllEffects();
  await flushPromises();

  expect(node.find('EntityName').prop('name')).toBe('Failing Name');
});

it('should pass an error message if there is an error and no report is returned', async () => {
  const error = {
    status: 400,
    errorMessage: 'Is failing',
    reportDefinition: null,
  };
  loadTile.mockReturnValueOnce(error);

  const mightFail = jest.fn((data, _success, fail) => fail(data));
  useErrorHandling.mockReturnValueOnce({
    mightFail,
  });
  const node = shallow(
    <OptimizeReportTile
      {...props}
      disableNameLink
      children={({onTileUpdate}) => (
        <button onClick={onTileUpdate} className="EditTile">
          edit
        </button>
      )}
    />
  );

  runAllEffects();
  await flushPromises();

  expect(node.find(ReportRenderer).prop('error')).toEqual(error);

  node.find('.EditTile').simulate('click');

  runAllEffects();
  await flushPromises();

  expect(node.find(ReportRenderer).prop('error')).toEqual(null);
});

it('should reload the report if the filter changes', async () => {
  const node = shallow(<OptimizeReportTile {...props} filter={[{type: 'runningInstancesOnly'}]} />);

  loadTile.mockClear();
  node.setProps({filter: [{type: 'suspendedInstancesOnly', data: null}]});

  runAllEffects();
  await flushPromises();

  expect(loadTile).toHaveBeenCalledWith(
    props.tile.id,
    [{type: 'suspendedInstancesOnly', data: null}],
    {}
  );
});

it('should navigate to the report when clicked', async () => {
  loadTile.mockReturnValueOnce({name: 'Report Name', id: 'a', data: {visualization: 'number'}});
  const redirectSpy = jest.fn();
  useHistory.mockReturnValue({push: redirectSpy});
  const node = shallow(<OptimizeReportTile {...props} />);

  runAllEffects();
  await flushPromises();

  node.find('.OptimizeReportTile').simulate('click', {target: document.createElement('div')});

  expect(redirectSpy).toHaveBeenCalledWith('report/a/');
});

it('should not navigate to the report if disableNameLink is specified', async () => {
  loadTile.mockReturnValueOnce({name: 'Report Name', id: 'a', data: {visualization: 'number'}});
  const redirectSpy = jest.fn();
  useHistory.mockReturnValueOnce({push: redirectSpy});
  const node = shallow(<OptimizeReportTile {...props} disableNameLink />);

  runAllEffects();
  await flushPromises();

  node.find('.OptimizeReportTile').simulate('click', {target: document.createElement('div')});

  expect(redirectSpy).not.toHaveBeenCalledWith('report/a/');
});

it('should not navigate to the report when clicking on button element', async () => {
  loadTile.mockReturnValueOnce({name: 'Report Name', id: 'a', data: {visualization: 'number'}});
  const redirectSpy = jest.fn();
  useHistory.mockReturnValueOnce({push: redirectSpy});
  const node = shallow(<OptimizeReportTile {...props} />);

  runAllEffects();
  await flushPromises();

  node.find('.OptimizeReportTile').simulate('click', {target: document.createElement('button')});

  expect(redirectSpy).not.toHaveBeenCalledWith('report/a/');
});

it('should not navigate to the report when clicking on an href element', async () => {
  loadTile.mockReturnValueOnce({name: 'Report Name', id: 'a', data: {visualization: 'table'}});
  const redirectSpy = jest.fn();
  useHistory.mockReturnValueOnce({push: redirectSpy});
  const node = shallow(<OptimizeReportTile {...props} />);

  runAllEffects();
  await flushPromises();

  node.find('.OptimizeReportTile').simulate('click', {target: document.createElement('a')});

  expect(redirectSpy).not.toHaveBeenCalledWith('report/a/');
});

it('should not navigate to the report when clicking on table visualization', async () => {
  loadTile.mockReturnValueOnce({name: 'Report Name', id: 'a', data: {visualization: 'table'}});
  const redirectSpy = jest.fn();
  useHistory.mockReturnValueOnce({push: redirectSpy});
  const node = shallow(<OptimizeReportTile {...props} />);

  runAllEffects();
  await flushPromises();

  const tableVisualization = document.createElement('div');
  tableVisualization.classList.add('visualization');

  node.find('.OptimizeReportTile').simulate('click', {target: tableVisualization});

  expect(redirectSpy).not.toHaveBeenCalledWith('report/a/');
});
