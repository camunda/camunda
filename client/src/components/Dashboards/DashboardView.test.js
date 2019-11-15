/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ThemedDashboardView from './DashboardView';

import {Dropdown} from 'components';

const {WrappedComponent: DashboardView} = ThemedDashboardView;

jest.mock('moment', () => () => {
  return {
    format: () => 'some date'
  };
});

it('should display the key properties of a dashboard', () => {
  const node = shallow(
    <DashboardView name="name" lastModifier="lastModifier" lastModified="unformatted date" />
  );

  expect(node.find('h1.name')).toIncludeText('name');
  expect(node.find('.metadata')).toIncludeText('lastModifier');
  expect(node.find('.metadata')).toIncludeText('some date');
});

it('should provide a link to edit mode in view mode', () => {
  const node = shallow(<DashboardView currentUserRole="editor" />);

  expect(node.find('.edit-button')).toExist();
});

it('should render a sharing popover', () => {
  const node = shallow(<DashboardView />);

  expect(node.find('Popover.share-button')).toExist();
});

it('should enter fullscreen mode', () => {
  const node = shallow(<DashboardView />);

  node.find('.fullscreen-button').simulate('click');

  expect(node.state('fullScreenActive')).toBe(true);
});

it('should leave fullscreen mode', () => {
  const node = shallow(<DashboardView />);
  node.setState({fullScreenActive: true});

  node.find('.fullscreen-button').simulate('click');

  expect(node.state('fullScreenActive')).toBe(false);
});

it('should activate auto refresh mode and set it to numeric value', () => {
  const node = shallow(<DashboardView />);

  node
    .find(Dropdown.Option)
    .last()
    .simulate('click');

  expect(typeof node.state('autoRefreshInterval')).toBe('number');
});

it('should deactivate autorefresh mode', () => {
  const node = shallow(<DashboardView />);
  node.setState({autoRefreshInterval: 1000});

  node
    .find(Dropdown.Option)
    .first()
    .simulate('click');

  expect(node.state('autoRefreshInterval')).toBe(null);
});

it('should add an autorefresh addon when autorefresh mode is active', () => {
  const node = shallow(<DashboardView />);
  node.setState({autoRefreshInterval: 1000});

  expect(node.find('DashboardRenderer').prop('reportAddons')).toMatchSnapshot();
});

it('should have a toggle theme button that is only visible in fullscreen mode', () => {
  const node = shallow(<DashboardView />);

  expect(node.find('.theme-toggle')).not.toExist();

  node.setState({fullScreenActive: true});

  expect(node.find('.theme-toggle')).toExist();
});

it('should toggle the theme when clicking the toggle theme button', () => {
  const spy = jest.fn();
  const node = shallow(<DashboardView />);
  node.setState({fullScreenActive: true});
  node.setProps({toggleTheme: spy});

  node.find('.theme-toggle').simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should return to light mode when exiting fullscreen mode', () => {
  const spy = jest.fn();
  const node = shallow(<DashboardView />);
  node.setState({fullScreenActive: true});
  node.setProps({toggleTheme: spy, theme: 'dark'});

  node.instance().changeFullScreen(false);

  expect(spy).toHaveBeenCalled();
});

it('should return to light mode when the component is unmounted', async () => {
  const spy = jest.fn();
  const node = shallow(<DashboardView />);

  node.setState({fullScreenActive: true});
  node.setProps({toggleTheme: spy, theme: 'dark'});

  node.unmount();

  expect(spy).toHaveBeenCalled();
});

it('should disable the share button if not authorized', () => {
  const node = shallow(<DashboardView isAuthorizedToShare={false} sharingEnabled />);

  const shareButton = node.find('.share-button');
  expect(shareButton).toBeDisabled();
  expect(shareButton.props()).toHaveProperty(
    'tooltip',
    "You are not authorized to share the dashboard because you don't have access to all reports on the dashboard!"
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
