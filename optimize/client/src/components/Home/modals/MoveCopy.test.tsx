/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';
import {runLastEffect} from '__mocks__/react';

import {loadEntities} from 'services';
import {EntityListEntity} from 'types';

import MoveCopy from './MoveCopy';

jest.mock('services', () => ({
  ...jest.requireActual('services'),
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
      reportType: 'process',
      entityType: 'report',
    },
    {
      id: 'anotherCollection',
      name: 'Another Collection',
      entityType: 'collection',
    },
  ]),
}));

jest.mock('hooks', () => ({
  useErrorHandling: jest.fn(() => ({
    mightFail: jest.fn((data, cb) => cb(data)),
    error: false,
    resetError: jest.fn(),
  })),
}));

const props = {
  entity: {
    name: 'Test Dashboard',
    entityType: 'dashboard',
    data: {subEntityCounts: {report: 2}},
  } as EntityListEntity,
  collection: null,
  moving: true,
  setMoving: jest.fn(),
  setCollection: jest.fn(),
  parentCollection: 'aCollectionId',
};

it('should render properly', () => {
  const node = shallow(<MoveCopy {...props} />);
  runLastEffect();

  expect(node.find('Toggle').prop('labelText')).toBe('Move copy to …');
  expect(node.find('ComboBox').prop('items')).toEqual([
    {id: null, name: 'Collections', entityType: 'collection'},
    {
      id: 'anotherCollection',
      name: 'Another Collection',
      entityType: 'collection',
    },
  ]);
});

it('should load available collections', () => {
  shallow(<MoveCopy {...props} />);
  runLastEffect();

  expect(loadEntities).toHaveBeenCalled();
});

it('should invoke setCopy on copy switch change', () => {
  const node = shallow(<MoveCopy {...props} />);

  node.find('Toggle').prop<(checked: boolean) => void>('onToggle')?.(false);

  expect(props.setMoving).toHaveBeenCalledWith(false);
});

it('should invoke setCollection on collection selection', () => {
  const node = shallow(<MoveCopy {...props} />);
  runLastEffect();

  node.find('ComboBox').prop<(args: {selectedItem: {id: string}}) => void>('onChange')?.({
    selectedItem: {id: 'anotherCollection'},
  });

  expect(props.setCollection).toHaveBeenCalledWith({
    id: 'anotherCollection',
    name: 'Another Collection',
    entityType: 'collection',
  });
});
