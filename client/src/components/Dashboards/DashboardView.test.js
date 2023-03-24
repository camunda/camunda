/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runAllEffects, runAllCleanups} from 'react';
import {shallow} from 'enzyme';
import {useFullScreenHandle} from 'react-full-screen';

import {AlertsDropdown} from 'components';
import {getOptimizeProfile} from 'config';

import {AutoRefreshSelect} from './AutoRefresh';
import {DashboardView} from './DashboardView';

jest.mock('config', () => ({
  getOptimizeProfile: jest.fn().mockReturnValue('platform'),
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

  expect(node.find('EntityName').prop('children')).toBe('name');
});

it('should provide a link to edit mode in view mode', () => {
  const node = shallow(<DashboardView currentUserRole="editor" />);

  expect(node.find('.edit-button')).toExist();
});

it('should render a sharing popover', async () => {
  const node = shallow(<DashboardView />);

  await runAllEffects();

  expect(node.find('Popover.share-button')).toExist();
});

it('should show sharing popover in cloud environment', async () => {
  getOptimizeProfile.mockReturnValueOnce('cloud');
  const node = shallow(<DashboardView />);

  await runAllEffects();

  expect(node.find('Popover.share-button')).toExist();
});

it('should hide alert dropdown in ccsm environment', async () => {
  getOptimizeProfile.mockReturnValueOnce('ccsm');
  const node = shallow(<DashboardView />);

  await runAllEffects();

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

  const shareButton = node.find('.share-button');
  expect(shareButton).toBeDisabled();
  expect(shareButton.props()).toHaveProperty(
    'tooltip',
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

  expect(node.find('.filter-button')).toHaveProp('active');
  expect(node.find('FiltersView')).toExist();

  node.find('.filter-button').simulate('click');

  expect(node.find('.filter-button').prop('active')).toBe(false);
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

it('should hide the share button', () => {
  const node = shallow(<DashboardView sharingHidden />);

  expect(node.find('Popover.share-button')).not.toExist();
});
