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

it('should load the report provided by id', () => {
  shallow(<OptimizeReportTile {...props} />);

  expect(loadTile).toHaveBeenCalledWith(props.tile.id, props.filter, {});
});

it('should render the ReportRenderer if data is loaded', async () => {
  loadTile.mockReturnValue('data');

  const node = shallow(<OptimizeReportTile {...props} />);

  await node.instance().loadTile();

  expect(node.find(ReportRenderer)).toExist();
});

it('should contain the report name', async () => {
  loadTile.mockReturnValue({name: 'Report Name'});
  const node = shallow(<OptimizeReportTile {...props} />);

  await node.instance().loadTile();

  expect(node.find('EntityName').children()).toIncludeText('Report Name');
});

it('should provide a link to the report', async () => {
  loadTile.mockReturnValue({name: 'Report Name', id: 'a'});
  const node = shallow(<OptimizeReportTile {...props} />);

  await node.instance().loadTile();
  node.update();

  expect(node.find('EntityName').children()).toIncludeText('Report Name');
  expect(node.find('EntityName')).toHaveProp('linkTo', 'report/a/');
});

it('should use the customizeTileLink prop to get the link if specified', async () => {
  loadTile.mockReturnValue({name: 'Report Name', id: 'a'});
  const node = shallow(<OptimizeReportTile {...props} customizeTileLink={(id) => `test/${id}/`} />);

  await node.instance().loadTile();
  node.update();

  expect(node.find('EntityName')).toHaveProp('linkTo', 'test/a/');
});

it('should not provide a link to the report when link is disabled', async () => {
  loadTile.mockReturnValue({name: 'Report Name'});
  const node = shallow(<OptimizeReportTile {...props} disableNameLink />);

  await node.instance().loadTile();
  node.update();

  expect(node.find('EntityName')).toHaveProp('linkTo', false);
  expect(node.find('EntityName').children()).toIncludeText('Report Name');
});

it('should display the name of a failing report', async () => {
  loadTile.mockReturnValue({
    reportDefinition: {name: 'Failing Name'},
  });
  const node = shallow(
    <OptimizeReportTile
      {...props}
      mightFail={(data, success, fail) => fail(data)}
      disableNameLink
    />
  );

  await node.instance().loadTile();

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
  await flushPromises();

  expect(node.find(ReportRenderer).prop('error')).toEqual(error);

  node.setProps({mightFail: props.mightFail});
  await node.instance().loadTile();

  expect(node.find(ReportRenderer).prop('error')).toEqual(null);
});

it('should reload the report if the filter changes', async () => {
  const node = shallow(<OptimizeReportTile {...props} filter={[{type: 'runningInstancesOnly'}]} />);

  await node.instance().loadTile();

  loadTile.mockClear();
  node.setProps({filter: [{type: 'suspendedInstancesOnly', data: null}]});

  expect(loadTile).toHaveBeenCalledWith(
    props.tile.id,
    [{type: 'suspendedInstancesOnly', data: null}],
    {}
  );
});
