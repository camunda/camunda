/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {nowDirty, isDirty} from 'saveGuard';
import {EntityNameForm} from 'components';
import {showPrompt} from 'prompt';
import {track} from 'tracking';

import {FiltersEdit} from './filters';

import {AutoRefreshSelect} from './AutoRefresh';
import {DashboardEdit} from './DashboardEdit';

jest.mock('saveGuard', () => ({
  nowDirty: jest.fn(),
  nowPristine: jest.fn(),
  isDirty: jest.fn().mockReturnValue(true),
}));
jest.mock('prompt', () => ({
  showPrompt: jest.fn().mockImplementation(async (_config, cb) => await cb()),
}));
jest.mock('./service', () => ({
  convertFilterToDefaultValues: () => null,
  getDefaultFilter: () => [],
}));
jest.mock('tracking', () => ({track: jest.fn()}));

beforeEach(() => {
  showPrompt.mockClear();
});

it('should contain an AddButton', () => {
  const node = shallow(<DashboardEdit />);

  expect(node.find('AddButton')).toExist();
});

it('should contain editing tile addons', () => {
  const node = shallow(<DashboardEdit />);

  expect(node.find('DashboardRenderer').prop('addons')).toMatchSnapshot();
});

it('should pass the isNew prop to the EntityNameForm', () => {
  const node = shallow(<DashboardEdit isNew />);

  expect(node.find(EntityNameForm).prop('isNew')).toBe(true);
});

it('should notify the saveGuard of changes', () => {
  const node = shallow(<DashboardEdit initialTiles={[]} />);

  node.setState({tiles: ['someTile']});

  expect(nowDirty).toHaveBeenCalled();
});

it('should react to layout changes', () => {
  const node = shallow(
    <DashboardEdit
      initialTiles={[
        {
          id: '1',
          position: {x: 0, y: 0},
          dimensions: {height: 2, width: 2},
        },
        {
          id: '2',
          position: {x: 3, y: 0},
          dimensions: {height: 4, width: 3},
        },
      ]}
    />
  );

  node.find('DashboardRenderer').prop('onLayoutChange')([
    {x: 0, y: 0, h: 4, w: 2},
    {x: 3, y: 2, h: 4, w: 3},
  ]);

  expect(node.state('tiles')).toMatchSnapshot();
});

it('should show FiltersEdit section if there are filters defined', () => {
  const node = shallow(<DashboardEdit initialAvailableFilters={[{}]} />);

  expect(node.find(FiltersEdit)).toExist();
});

it('should save the dashboard when going to the report edit mode', async () => {
  const report = {
    type: 'optimize_report',
    position: {x: 0, y: 0},
    dimensions: {height: 2, width: 2},
    report: {id: 'new'},
  };
  const saveSpy = jest.fn();
  const historySpy = jest.fn();

  const node = shallow(
    <DashboardEdit
      initialTiles={[report]}
      saveChanges={saveSpy}
      history={{push: historySpy}}
      location={{pathname: 'dashboard/1/edit'}}
    />
  );

  node.find('DashboardRenderer').prop('onTileUpdate')(report);

  await flushPromises();

  // Parent component takes care of saving the tiles and assigning ids
  node.setProps({
    initialTiles: [
      {
        position: {x: 0, y: 0},
        dimensions: {height: 2, width: 2},
        id: '1',
      },
    ],
  });

  await flushPromises();

  expect(saveSpy).toHaveBeenCalled();
  expect(historySpy).toHaveBeenCalledWith('report/1/edit?returnTo=dashboard/1/edit');
});

it('should not prompt to save the dashboard when going to the edit mode when there are no changes', () => {
  const report = {
    type: 'optimize_report',
    position: {x: 0, y: 0},
    dimensions: {height: 2, width: 2},
    id: '1',
  };
  const historySpy = jest.fn();

  const node = shallow(
    <DashboardEdit
      initialTiles={[report]}
      history={{push: historySpy}}
      location={{pathname: 'dashboard/1/edit'}}
    />
  );

  isDirty.mockReturnValueOnce(false);
  node.find('DashboardRenderer').prop('onTileUpdate')(report);

  expect(showPrompt).not.toHaveBeenCalled();
  expect(historySpy).toHaveBeenCalledWith('report/1/edit?returnTo=dashboard/1/edit');
});

it('should save basic dashboard info', async () => {
  const saveSpy = jest.fn();
  const intervalSeconds = 60;
  const stayInEditMode = false;
  const dashboardName = 'dashboardName';

  const node = shallow(<DashboardEdit initialTiles={[]} saveChanges={saveSpy} />);

  node.find(AutoRefreshSelect).simulate('change', intervalSeconds * 1000);
  node.find(EntityNameForm).simulate('change', {target: {value: dashboardName}});
  node.find(EntityNameForm).simulate('save', stayInEditMode);

  await flushPromises();

  expect(saveSpy).toHaveBeenCalledWith(
    dashboardName,
    undefined,
    [],
    [],
    intervalSeconds,
    stayInEditMode
  );
});

it('should add tile', () => {
  jest.spyOn(document, 'querySelector').mockReturnValue({
    lastChild: {
      getBoundingClientRect: () => ({}),
      dispatchEvent: jest.fn(),
      addEventListener: jest.fn(),
    },
  });

  const newTextTile = {
    position: {x: 0, y: 0},
    id: '',
    configuration: {text: 'text'},
    type: 'text',
  };
  const node = shallow(<DashboardEdit initialTiles={[]} />);

  node.find('AddButton').prop('addTile')(newTextTile);

  expect(node.state('tiles')[0]).toEqual(newTextTile);
  expect(track).toHaveBeenCalledWith('createTextTile', {entityId: ''});
});

it('should update tile', () => {
  const textTile = {
    position: {x: 0, y: 0},
    id: '',
    configuration: {text: 'text'},
    type: 'text',
  };
  const node = shallow(<DashboardEdit initialTiles={[textTile]} />);

  const newTextTile = {
    ...textTile,
    configuration: {text: 'newText'},
  };
  node.find('DashboardRenderer').prop('onTileUpdate')(newTextTile);

  expect(node.state('tiles')[0]).toEqual(newTextTile);
  expect(track).toHaveBeenCalledWith('updateTextTile', {entityId: ''});
});

it('should delete tile', () => {
  const textTile = {
    position: {x: 0, y: 0},
    id: '',
    configuration: {text: 'text'},
    type: 'text',
  };
  const node = shallow(<DashboardEdit initialTiles={[textTile]} />);

  node.find('DashboardRenderer').prop('onTileDelete')(textTile);

  expect(node.state('tiles')).toEqual([]);
  expect(track).toHaveBeenCalledWith('deleteTextTile', {entityId: ''});
});

it('should update description', () => {
  const node = shallow(<DashboardEdit id="id" initialTiles={[]} />);

  node.find(EntityNameForm).prop('onDescriptionChange')('some description');

  expect(node.state('description')).toEqual('some description');
  expect(track).toHaveBeenCalledWith('editDescription', {entity: 'dashboard', entityId: 'id'});
});

it('should use first optimize report to fetch report definition when creating new report', () => {
  const textTile = {
    position: {x: 0, y: 0},
    id: '',
    configuration: {text: 'text'},
    type: 'text',
  };

  const report = {
    position: {x: 1, y: 1},
    id: 'someId',
    type: 'optimize_report',
    report: {},
  };
  const node = shallow(<DashboardEdit initialTiles={[textTile, report]} />);

  expect(node.find('AddButton').prop('existingReport')).toEqual(report);
});
