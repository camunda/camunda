import React from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import Dashboards from './Dashboards';
jest.mock('./service');

const dashboard = {
  id: 'dashboardID',
  name: 'Some Dashboard',
  lastModifier: 'Admin',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reports: []
};

const dashboards = new Array(7).fill(dashboard);

const props = {
  dashboards: [dashboard],
  duplicateDashboard: jest.fn(),
  createDashboard: jest.fn(),
  showDeleteModalFor: jest.fn()
};

it('should show information about dashboards', () => {
  const node = shallow(<Dashboards {...props} />);

  expect(node.find('.dataTitle')).toIncludeText('Some Dashboard');
});

it('should show a link that goes to the dashboard', () => {
  const node = shallow(<Dashboards {...props} />);

  expect(node.find('li > Link').prop('to')).toBe('/dashboard/dashboardID');
});

it('should show no data indicator', () => {
  const node = shallow(<Dashboards {...props} dashboards={[]} />);

  expect(node.find('NoEntities')).toBePresent();
});

it('should contain a link to the edit mode of the dashboard', () => {
  const node = shallow(<Dashboards {...props} />);

  expect(node.find('.operations Link').prop('to')).toBe('/dashboard/dashboardID/edit');
});

it('should invok duplicate dashboards when clicking duplicate icon', () => {
  const node = shallow(<Dashboards {...props} />);

  node
    .find('.operations')
    .find(Button)
    .first()
    .simulate('click', {target: {blur: jest.fn()}});

  expect(props.duplicateDashboard).toHaveBeenCalledWith(dashboard);
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
  const node = shallow(<Dashboards {...props} dashboards={dashboards} />);

  expect(node).toIncludeText('Show all');
});

it('should show a button to show all entities if the number of entities is greater than 5', () => {
  const node = shallow(<Dashboards {...props} dashboards={dashboards} />);

  const button = node.find(Button).filter('[type="link"]');

  button.simulate('click');

  expect(node).toIncludeText('Show less...');
});
