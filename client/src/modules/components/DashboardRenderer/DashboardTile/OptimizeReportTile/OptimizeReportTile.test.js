/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {OptimizeReportTile} from './OptimizeReportTile';
import {ReportRenderer} from 'components';

const loadTile = jest.fn().mockReturnValue({id: 'a'});

const props = {
  tile: {
    id: 'a',
  },
  filter: [{type: 'runningInstancesOnly', data: null}],
  loadTile,
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

beforeEach(() => {
  jest.clearAllMocks();
});

it('should load the report provided by id', () => {
  shallow(<OptimizeReportTile {...props} />);

  expect(loadTile).toHaveBeenCalledWith(props.tile.id, props.filter, {});
});

it('should render the ReportRenderer if data is loaded', async () => {
  loadTile.mockReturnValueOnce('data');

  const node = await shallow(<OptimizeReportTile {...props} />);

  expect(node.find(ReportRenderer)).toExist();
});

it('should contain the report name', async () => {
  loadTile.mockReturnValueOnce({name: 'Report Name'});
  const node = await shallow(<OptimizeReportTile {...props} />);

  expect(node.find('EntityName').children()).toIncludeText('Report Name');
});

it('should provide a link to the report', async () => {
  loadTile.mockReturnValueOnce({name: 'Report Name', id: 'a'});
  const node = await shallow(<OptimizeReportTile {...props} />);

  expect(node.find('EntityName').children()).toIncludeText('Report Name');
  expect(node.find('EntityName')).toHaveProp('linkTo', 'report/a/');
});

it('should use the customizeTileLink prop to get the link if specified', async () => {
  loadTile.mockReturnValueOnce({name: 'Report Name', id: 'a'});
  const node = await shallow(
    <OptimizeReportTile {...props} customizeTileLink={(id) => `test/${id}/`} />
  );

  expect(node.find('EntityName')).toHaveProp('linkTo', 'test/a/');
});

it('should not provide a link to the report when link is disabled', async () => {
  loadTile.mockReturnValueOnce({name: 'Report Name'});
  const node = await shallow(<OptimizeReportTile {...props} disableNameLink />);

  expect(node.find('EntityName')).toHaveProp('linkTo', false);
  expect(node.find('EntityName').children()).toIncludeText('Report Name');
});

it('should display the name of a failing report', async () => {
  loadTile.mockReturnValueOnce({
    reportDefinition: {name: 'Failing Name'},
  });
  const node = await shallow(
    <OptimizeReportTile
      {...props}
      mightFail={(data, success, fail) => fail(data)}
      disableNameLink
    />
  );
  expect(node.find('EntityName').children()).toIncludeText('Failing Name');
});

it('should pass an error message if there is an error and no report is returned', async () => {
  const error = {
    status: 400,
    errorMessage: 'Is failing',
    reportDefinition: null,
  };
  loadTile.mockReturnValueOnce(error);

  const node = await shallow(
    <OptimizeReportTile
      {...props}
      mightFail={(data, success, fail) => fail(data)}
      disableNameLink
    />
  );

  expect(node.find(ReportRenderer).prop('error')).toEqual(error);

  node.setProps({mightFail: props.mightFail});
  await node.instance().loadTile();

  expect(node.find(ReportRenderer).prop('error')).toEqual(null);
});

it('should reload the report if the filter changes', async () => {
  const node = await shallow(
    <OptimizeReportTile {...props} filter={[{type: 'runningInstancesOnly'}]} />
  );

  loadTile.mockClear();
  node.setProps({filter: [{type: 'suspendedInstancesOnly', data: null}]});

  expect(loadTile).toHaveBeenCalledWith(
    props.tile.id,
    [{type: 'suspendedInstancesOnly', data: null}],
    {}
  );
});

it('should navigate to the report when clicked', async () => {
  loadTile.mockReturnValueOnce({name: 'Report Name', id: 'a', data: {visualization: 'number'}});
  const redirectSpy = jest.fn();
  const node = await shallow(<OptimizeReportTile {...props} history={{push: redirectSpy}} />);

  node.find('.OptimizeReportTile').simulate('click', {target: document.createElement('div')});

  expect(redirectSpy).toHaveBeenCalledWith('report/a/');
});

it('should not navigate to the report if disableNameLink is specified', async () => {
  loadTile.mockReturnValueOnce({name: 'Report Name', id: 'a', data: {visualization: 'number'}});
  const redirectSpy = jest.fn();
  const node = await shallow(
    <OptimizeReportTile {...props} history={{push: redirectSpy}} disableNameLink />
  );

  node.find('.OptimizeReportTile').simulate('click', {target: document.createElement('div')});

  expect(redirectSpy).not.toHaveBeenCalledWith('report/a/');
});

it('should not navigate to the report when clicking on button element', async () => {
  loadTile.mockReturnValueOnce({name: 'Report Name', id: 'a', data: {visualization: 'number'}});
  const redirectSpy = jest.fn();
  const node = await shallow(<OptimizeReportTile {...props} history={{push: redirectSpy}} />);

  node.find('.OptimizeReportTile').simulate('click', {target: document.createElement('button')});

  expect(redirectSpy).not.toHaveBeenCalledWith('report/a/');
});

it('should not navigate to the report when clicking on an href element', async () => {
  loadTile.mockReturnValueOnce({name: 'Report Name', id: 'a', data: {visualization: 'table'}});
  const redirectSpy = jest.fn();
  const node = await shallow(<OptimizeReportTile {...props} history={{push: redirectSpy}} />);

  node.find('.OptimizeReportTile').simulate('click', {target: document.createElement('a')});

  expect(redirectSpy).not.toHaveBeenCalledWith('report/a/');
});

it('should not navigate to the report when clicking on table visualization', async () => {
  loadTile.mockReturnValueOnce({name: 'Report Name', id: 'a', data: {visualization: 'table'}});
  const redirectSpy = jest.fn();
  const node = await shallow(<OptimizeReportTile {...props} history={{push: redirectSpy}} />);

  const tableVisualization = document.createElement('div');
  tableVisualization.classList.add('visualization');

  node.find('.OptimizeReportTile').simulate('click', {target: tableVisualization});

  expect(redirectSpy).not.toHaveBeenCalledWith('report/a/');
});
