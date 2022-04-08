/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Switch} from 'components';

import MoveCopyWithErrorHandling from './MoveCopy';

import {loadEntities} from '../service';

const MoveCopy = MoveCopyWithErrorHandling.WrappedComponent;

jest.mock('../service', () => ({
  loadEntities: jest.fn().mockReturnValue([
    {
      id: 'aCollectionId',
      name: 'aCollectionName',
      lastModified: '2017-11-11T11:11:11.1111',
      created: '2017-11-11T11:11:11.1111',
      owner: 'user_id',
      lastModifier: 'user_id',
      entityType: 'collection',
      currentUserRole: 'manager',
      data: {
        subEntityCounts: {
          dashboard: 2,
          report: 8,
        },
        roleCounts: {
          user: 5,
          group: 2,
        },
      },
    },
    {
      id: 'aDashboardId',
      name: 'aDashboard',
      lastModified: '2017-11-11T11:11:11.1111',
      created: '2017-11-11T11:11:11.1111',
      owner: 'user_id',
      entityType: 'dashboard',
      lastModifier: 'user_id',
      currentUserRole: 'editor',
      data: {
        subEntityCounts: {
          report: 8,
        },
        roleCounts: {},
      },
    },
    {
      id: 'aReportId',
      name: 'aReport',
      lastModified: '2017-11-11T11:11:11.1111',
      created: '2017-11-11T11:11:11.1111',
      owner: 'user_id',
      currentUserRole: 'editor',
      data: {subEntityCounts: {}, roleCounts: {}},
      lastModifier: 'user_id',
      reportType: 'process', // or 'decision'
      combined: false,
      entityType: 'report',
    },
    {
      id: 'anotherCollection',
      name: 'Another Collection',
      entityType: 'collection',
    },
  ]),
}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  entity: {name: 'Test Dashboard', entityType: 'dashboard', data: {subEntityCounts: {report: 2}}},
  collection: null,
  moving: true,
  setMoving: jest.fn(),
  setCollection: jest.fn(),
  parentCollection: 'aCollectionId',
};

it('should match snapshot', () => {
  const node = shallow(<MoveCopy {...props} />);

  expect(node).toMatchSnapshot();
});

it('should load available collections', () => {
  shallow(<MoveCopy {...props} />);

  expect(loadEntities).toHaveBeenCalled();
});

it('should invoke setCopy on copy switch change', async () => {
  const node = shallow(<MoveCopy {...props} />);

  node.find(Switch).simulate('change', {target: {checked: false}});

  expect(props.setMoving).toHaveBeenCalledWith(false);
});

it('should invoke setCollection on collection selection', () => {
  const node = shallow(<MoveCopy {...props} />);

  node.find('Typeahead').props().onChange('anotherCollection');

  expect(props.setCollection).toHaveBeenCalledWith({
    id: 'anotherCollection',
    name: 'Another Collection',
    entityType: 'collection',
  });
});
