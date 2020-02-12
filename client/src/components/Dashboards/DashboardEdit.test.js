/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {nowDirty} from 'saveGuard';

import DashboardEdit from './DashboardEdit';

jest.mock('saveGuard', () => ({nowDirty: jest.fn(), nowPristine: jest.fn()}));

it('should contain an AddButton', () => {
  const node = shallow(<DashboardEdit />);

  expect(node.find('AddButton')).toExist();
});

it('should editing report addons', () => {
  const node = shallow(<DashboardEdit />);

  expect(node.find('DashboardRenderer').prop('addons')).toMatchSnapshot();
});

it('should pass the isNew prop to the EntityNameForm', () => {
  const node = shallow(<DashboardEdit isNew />);

  expect(node.find('EntityNameForm').prop('isNew')).toBe(true);
});

it('should notify the saveGuard of changes', () => {
  const node = shallow(<DashboardEdit initialReports={[]} />);

  node.setState({reports: ['someReport']});

  expect(nowDirty).toHaveBeenCalled();
});

it('should react to layout changes', () => {
  const node = shallow(
    <DashboardEdit
      initialReports={[
        {
          id: '1',
          position: {x: 0, y: 0},
          dimensions: {height: 2, width: 2}
        },
        {
          id: '2',
          position: {x: 3, y: 0},
          dimensions: {height: 4, width: 3}
        }
      ]}
    />
  );

  node.find('DashboardRenderer').prop('onChange')([
    {x: 0, y: 0, h: 4, w: 2},
    {x: 3, y: 2, h: 4, w: 3}
  ]);

  expect(node.state('reports')).toMatchSnapshot();
});
