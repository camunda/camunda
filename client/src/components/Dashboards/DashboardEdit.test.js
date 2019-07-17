/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import DashboardEdit from './DashboardEdit';

it('should contain a Grid', () => {
  const node = shallow(<DashboardEdit />);

  expect(node.find('Grid')).toExist();
});

it('should contain an AddButton', () => {
  const node = shallow(<DashboardEdit />);

  expect(node.find('AddButton')).toExist();
});

it('should editing report addons', () => {
  const node = shallow(<DashboardEdit />);

  expect(node.find('DashboardRenderer').prop('reportAddons')).toMatchSnapshot();
});

it('should hide the AddButton based on the state', () => {
  const node = shallow(<DashboardEdit />);
  node.setState({addButtonVisible: false});

  expect(node.find('AddButton').prop('visible')).toBe(false);
});

it('should pass the isNew prop to the EntityNameForm', () => {
  const node = shallow(<DashboardEdit isNew />);

  expect(node.find('EntityNameForm').prop('isNew')).toBe(true);
});
