import React from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import DashboardsWithErrorHandling from './Dashboards';
import {loadDashboards, createDashboard} from './service';

jest.mock('./service');

const Dashboards = DashboardsWithErrorHandling.WrappedComponent;

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data))
};

const dashboard = {
  id: 'dashboardID',
  name: 'Some Dashboard',
  lastModifier: 'Admin',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reports: []
};

beforeAll(() => {
  loadDashboards.mockReturnValue([dashboard]);
});

it('should show a loading indicator', () => {
  const node = shallow(<Dashboards {...props} />);

  node.setState({loading: true});

  expect(node.find('LoadingIndicator')).toBePresent();
});

it('should load data', () => {
  shallow(<Dashboards {...props} />);

  expect(loadDashboards).toHaveBeenCalled();
});

it('should show information about dashboards', () => {
  const node = shallow(<Dashboards {...props} />);

  expect(node.find('.dataTitle')).toIncludeText('Some Dashboard');
});

it('should show a link that goes to the dashboard', () => {
  const node = shallow(<Dashboards {...props} />);

  expect(node.find('li > Link').prop('to')).toBe('/dashboard/dashboardID');
});

it('should show no data indicator', () => {
  loadDashboards.mockReturnValueOnce([]);
  const node = shallow(<Dashboards {...props} />);

  expect(node.find('NoEntities')).toBePresent();
});

it('should contain a link to the edit mode of the dashboard', () => {
  const node = shallow(<Dashboards {...props} />);

  expect(node.find('.operations Link').prop('to')).toBe('/dashboard/dashboardID/edit');
});

it('should display error messages', () => {
  const node = shallow(<Dashboards {...props} error="Something went wrong" />);

  expect(node.find('Message')).toBePresent();
});

it('should show create dashboard buttons', () => {
  const node = shallow(<Dashboards {...props} />);

  expect(node.find('.createButton')).toBePresent();
});

it('should redirect to new dashboard edit page', async () => {
  createDashboard.mockReturnValueOnce('newDashboard');
  const node = shallow(<Dashboards {...props} />);

  await node.find('.createButton').simulate('click');

  expect(node.find('Redirect')).toBePresent();
  expect(node.find('Redirect').prop('to')).toBe('/dashboard/newDashboard/edit?new');
});

it('should show confirmation modal when deleting dashboard', () => {
  const node = shallow(<Dashboards {...props} />);

  node
    .find('.operations')
    .find(Button)
    .last()
    .simulate('click');

  expect(node.state('deleting')).toEqual(dashboard);
});

it('should duplicate dashboards', () => {
  createDashboard.mockClear();

  const node = shallow(<Dashboards {...props} />);

  node
    .find('.operations')
    .find(Button)
    .first()
    .simulate('click', {target: {blur: jest.fn()}});

  expect(createDashboard).toHaveBeenCalledWith({
    ...dashboard,
    name: dashboard.name + ' - Copy'
  });
});

it('should reload the list after duplication', async () => {
  const node = shallow(<Dashboards {...props} />);

  loadDashboards.mockClear();

  await node
    .find('.operations')
    .find(Button)
    .first()
    .simulate('click', {target: {blur: jest.fn()}});

  expect(loadDashboards).toHaveBeenCalled();
});

it('should contain a button to collapse the entities list', () => {
  const node = shallow(<Dashboards {...props} />);

  expect(node.find('ToggleButton')).toBePresent();
});

it('should hide the list of entities when clicking the collapse buttons', () => {
  const node = shallow(<Dashboards {...props} />);

  const button = node
    .find('ToggleButton')
    .dive()
    .find('.ToggleCollapse');

  button.simulate('click');

  expect(node.find('.entityList')).not.toBePresent();
});

it('should not show a button to show all entities if the number of entities is less than 5', () => {
  const node = shallow(<Dashboards {...props} />);

  expect(node).not.toIncludeText('Show all');
});

it('should show a button to show all entities if the number of entities is greater than 5', () => {
  loadDashboards.mockReturnValue([
    dashboard,
    dashboard,
    dashboard,
    dashboard,
    dashboard,
    dashboard
  ]);
  const node = shallow(<Dashboards {...props} />);

  expect(node).toIncludeText('Show all');
});

it('should show a button to show all entities if the number of entities is greater than 5', () => {
  const node = shallow(<Dashboards {...props} />);

  const button = node.find(Button).filter('[type="link"]');

  button.simulate('click');

  expect(node).toIncludeText('Show less...');
});
