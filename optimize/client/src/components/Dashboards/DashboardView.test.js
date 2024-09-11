/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {runAllEffects, runAllCleanups} from 'react';
import {shallow} from 'enzyme';
import {useFullScreenHandle} from 'react-full-screen';

import {AlertsDropdown} from 'components';
import {createEntity, deleteEntity, addSources} from 'services';
import {useUiConfig} from 'hooks';

import {AutoRefreshSelect} from './AutoRefresh';
import {DashboardView} from './DashboardView';
import {loadEntities} from 'services';

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  createEntity: jest.fn().mockReturnValue('collectionId'),
  deleteEntity: jest.fn(),
  addSources: jest.fn(),
  loadEntities: jest.fn().mockReturnValue([]),
}));

jest.mock('hooks', () => ({
  useUiConfig: jest.fn().mockReturnValue({userSearchAvailable: true}),
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
  useUser: jest.fn().mockImplementation(() => ({
    user: {name: 'User'},
  })),
}));

jest.mock('react-full-screen', () => {
  const handle = {active: false, exit: jest.fn(), enter: jest.fn()};
  return {
    FullScreen: (props) => <div {...props} />,
    useFullScreenHandle: () => handle,
  };
});
jest.mock('./service', () => ({
  getDefaultFilter: () => [],
}));

beforeEach(() => {
  useFullScreenHandle().active = false;
  useFullScreenHandle().enter.mockClear();
  useFullScreenHandle().exit.mockClear();
});

it('should display the key properties of a dashboard', () => {
  const node = shallow(
    <DashboardView
      name="name"
      lastModifier="lastModifier"
      lastModified="2020-11-11T11:11:11.1111+0200"
    />
  );

  expect(node.find('EntityName').prop('name')).toBe('name');
});

it('should provide a link to edit mode in view mode', () => {
  const node = shallow(<DashboardView currentUserRole="editor" />);

  expect(node.find('.edit-button')).toExist();
});

it('should render a sharing popover', async () => {
  const node = shallow(<DashboardView />);

  await runAllEffects();

  expect(node.find('.share-button')).toExist();
});

it('should hide alert dropdown if usersearch is not available', async () => {
  useUiConfig.mockReturnValueOnce({userSearchAvailable: false});
  const node = shallow(<DashboardView />);

  expect(node.find(AlertsDropdown)).not.toExist();
});

it('should enter fullscreen mode', () => {
  const node = shallow(<DashboardView />);

  node.find('.fullscreen-button').simulate('click');

  expect(useFullScreenHandle().enter).toHaveBeenCalled();
});

it('should leave fullscreen mode', () => {
  const node = shallow(<DashboardView />);
  useFullScreenHandle().active = true;

  node.find('.fullscreen-button').simulate('click');

  expect(useFullScreenHandle().exit).toHaveBeenCalled();
});

it('should set auto refresh value and pass it to the refresh behavior addon', () => {
  const node = shallow(<DashboardView />);

  node.find(AutoRefreshSelect).simulate('change', 'number');

  expect(node.find(AutoRefreshSelect).prop('refreshRateMs')).toBe('number');
  expect(node.find('DashboardRenderer').prop('addons')).toMatchSnapshot();
});

it('should pass the refresh interval prop to the refreshSelect and refresh bahavior components', () => {
  const intervalSeconds = 60;
  const node = shallow(<DashboardView refreshRateSeconds={intervalSeconds} />);

  expect(node.find(AutoRefreshSelect).prop('refreshRateMs')).toBe(intervalSeconds * 1000);
  expect(node.find('DashboardRenderer').prop('addons')).toMatchSnapshot();
});

it('should have a toggle theme button that is only visible in fullscreen mode', () => {
  let node = shallow(<DashboardView />);

  expect(node.find('.theme-toggle')).not.toExist();

  useFullScreenHandle().active = true;
  node = shallow(<DashboardView />);

  expect(node.find('.theme-toggle')).toExist();
});

it('should toggle the theme when clicking the toggle theme button', () => {
  const spy = jest.fn();
  useFullScreenHandle().active = true;
  const node = shallow(<DashboardView toggleTheme={spy} />);

  node.find('.theme-toggle').simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should return to light mode when exiting fullscreen mode', () => {
  const spy = jest.fn();
  useFullScreenHandle().active = true;
  const node = shallow(<DashboardView toggleTheme={spy} theme="dark" />);

  node.find('FullScreen').simulate('change');

  expect(spy).toHaveBeenCalled();
});

it('should return to light mode when the component is unmounted', async () => {
  const spy = jest.fn();
  useFullScreenHandle().active = true;
  shallow(<DashboardView toggleTheme={spy} theme="dark" />);

  await runAllEffects();
  await runAllCleanups();

  expect(spy).toHaveBeenCalled();
});

it('should hide alert dropdown in full screen mode', async () => {
  useFullScreenHandle().active = true;
  const node = shallow(<DashboardView />);

  await runAllEffects();

  expect(node.find(AlertsDropdown)).not.toExist();
});

it('should disable the share button if not authorized', async () => {
  const node = shallow(<DashboardView isAuthorizedToShare={false} sharingEnabled />);

  await runAllEffects();

  const shareButton = shallow(node.find('.share-button').prop('trigger'));
  expect(shareButton).toBeDisabled();
  expect(node.find('Popover').prop('tooltip')).toBe(
    'Sharing forbidden. Missing authorization for contained report.'
  );
});

it('should enable share button if authorized', () => {
  const node = shallow(<DashboardView isAuthorizedToShare sharingEnabled />);

  expect(node.find('.share-button')).not.toBeDisabled();
});

it('should hide edit/delete if the dashboard current user role is not "editor"', () => {
  const node = shallow(<DashboardView currentUserRole="viewer" />);

  expect(node.find('.delete-button')).not.toExist();
  expect(node.find('.edit-button')).not.toExist();
});

it('should show a filters toggle button if filters are available', () => {
  const node = shallow(<DashboardView availableFilters={[{type: 'state'}]} />);

  expect(node.find('.filter-button')).toExist();
});

it('should toggle filters section', () => {
  const node = shallow(<DashboardView availableFilters={[{type: 'state'}]} />);

  expect(node.find('.filter-button')).toHaveProp('isSelected');
  expect(node.find('FiltersView')).toExist();

  node.find('.filter-button').simulate('click');

  expect(node.find('.filter-button').prop('isSelected')).toBe(false);
  expect(node.find('FiltersView')).not.toExist();
});

it('should reset filters when closing the filters section', () => {
  const filter = [{type: 'runningInstancesOnly', data: null}];
  const node = shallow(<DashboardView availableFilters={[{type: 'state'}]} />);

  node.find('FiltersView').prop('setFilter')(filter);

  expect(node.find('FiltersView').prop('filter')).toEqual(filter);
  node.find('.filter-button').simulate('click');
  node.find('.filter-button').simulate('click');
  expect(node.find('FiltersView').prop('filter')).toEqual([]);
});

it('should hide the share button for instant preview dashboard', () => {
  const node = shallow(<DashboardView isInstantDashboard />);

  expect(node.find('.share-button')).not.toExist();
});

it('should show the description', () => {
  const node = shallow(<DashboardView description="description" />);

  expect(node.find('EntityDescription').prop('description')).toBe('description');
});

it('should hide the description', () => {
  const node = shallow(<DashboardView description={null} />);

  expect(node.find('EntityDescription')).not.toExist();
});

it('should render the create copy button and modal for instant preview dashboard', () => {
  const node = shallow(<DashboardView isInstantDashboard />);

  const createCopyButton = node.find('.create-copy');
  expect(createCopyButton).toExist();

  createCopyButton.simulate('click');

  expect(node.find('DashboardTemplateModal')).toExist();
  expect(node.find('DashboardTemplateModal').prop('trackingEventName')).toBe(
    'useInstantPreviewDashboardTemplate'
  );
});

it('should create a collection with the current data source when copying instant dashboard if one fot this user doesnt exist', async () => {
  loadEntities.mockReturnValueOnce([
    {
      name: 'someKey',
      id: 'someId',
      owner: 'OtherUser',
    },
  ]);
  const node = shallow(<DashboardView isInstantDashboard />);

  const createCopyButton = node.find('.create-copy');

  createCopyButton.simulate('click');

  node.find('DashboardTemplateModal').prop('onConfirm')({
    definitions: [{key: 'someKey', name: 'Def Name', tenantIds: []}],
  });

  await flushPromises();

  expect(createEntity).toHaveBeenCalledWith('collection', {name: 'Def Name'});
  expect(addSources).toHaveBeenCalledWith('collectionId', [
    {definitionKey: 'someKey', definitionType: 'process', tenants: []},
  ]);
});

it('should delete collection in case of error during collection creation', async () => {
  addSources.mockRejectedValue(new Error());
  const node = shallow(<DashboardView isInstantDashboard />);

  const createCopyButton = node.find('.create-copy');

  createCopyButton.simulate('click');

  node.find('DashboardTemplateModal').prop('onConfirm')({
    definitions: [{key: 'someKey', tenantIds: []}],
  });

  await flushPromises();

  expect(deleteEntity).toHaveBeenCalledWith('collection', 'collectionId');
});

it('should use existing collection with the same name if there is one for the current user', async () => {
  loadEntities.mockReturnValueOnce([
    {
      name: 'someKey',
      id: 'someId',
      owner: 'User',
    },
  ]);
  const node = shallow(<DashboardView isInstantDashboard />);

  const createCopyButton = node.find('.create-copy');

  createCopyButton.simulate('click');

  node.find('DashboardTemplateModal').prop('onConfirm')({
    definitions: [{key: 'someKey', tenantIds: []}],
  });

  await flushPromises();

  expect(loadEntities).toHaveBeenCalled();
  expect(addSources).toHaveBeenCalledWith('someId', [
    {definitionKey: 'someKey', definitionType: 'process', tenants: []},
  ]);
});

it('should use dashboard template name for copied instant dashboard with multiple data source', async () => {
  const templateName = 'Process performance overview';
  loadEntities.mockReturnValueOnce([
    {
      name: 'key1',
      id: 'id1',
      owner: 'User',
    },
    {
      name: 'key2',
      id: 'id2',
      owner: 'User',
    },
  ]);
  const node = shallow(<DashboardView isInstantDashboard />);

  const createCopyButton = node.find('.create-copy');

  createCopyButton.simulate('click');

  node.find('DashboardTemplateModal').prop('onConfirm')({
    name: templateName,
    definitions: [
      {key: 'key1', tenantIds: []},
      {key: 'key2', tenantIds: []},
    ],
  });

  await flushPromises();

  expect(loadEntities).toHaveBeenCalled();
  expect(createEntity).toHaveBeenCalledWith('collection', {name: templateName});
  expect(addSources).toHaveBeenCalledWith('collectionId', [
    {definitionKey: 'key1', definitionType: 'process', tenants: []},
    {definitionKey: 'key2', definitionType: 'process', tenants: []},
  ]);
});

it('should not delete collection in case of error if collection existed', async () => {
  loadEntities.mockReturnValueOnce([
    {
      name: 'someKey',
      id: 'someId',
    },
  ]);
  addSources.mockRejectedValue(new Error());
  const node = shallow(<DashboardView isInstantDashboard />);

  const createCopyButton = node.find('.create-copy');

  createCopyButton.simulate('click');

  node.find('DashboardTemplateModal').prop('onConfirm')({
    definitions: [{key: 'someKey', tenantIds: []}],
  });

  await flushPromises();

  expect(deleteEntity).not.toHaveBeenCalled();
});

it('should default available filters to an empty array', () => {
  const node = shallow(<DashboardView />);

  expect(node.find('DashboardRenderer').prop('filter')).toEqual([]);
});
