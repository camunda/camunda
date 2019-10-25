/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Switch} from 'components';

import CopyModalWithErrorHandling from './CopyModal';

import {loadEntities} from './service';

const CopyModal = CopyModalWithErrorHandling.WrappedComponent;

jest.mock('./service', () => ({
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
          report: 8
        },
        roleCounts: {
          user: 5,
          group: 2
        }
      }
    },
    {
      id: 'aDashboardId',
      name: 'aDashboard',
      lastModified: '2017-11-11T11:11:11.1111',
      created: '2017-11-11T11:11:11.1111',
      owner: 'user_id',
      lastModifier: 'user_id',
      entityType: 'dashboard',
      currentUserRole: 'editor',
      data: {
        subEntityCounts: {
          report: 8
        },
        roleCounts: {}
      }
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
      entityType: 'report'
    }
  ])
}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  entity: {name: 'Test Dashboard', entityType: 'dashboard', data: {subEntityCounts: {report: 2}}},
  collection: 'aCollectionId',
  onConfirm: jest.fn(),
  onClose: jest.fn()
};

it('should match snapshot', () => {
  const node = shallow(<CopyModal {...props} />);

  expect(node).toMatchSnapshot();
});

it('should load available collections', () => {
  shallow(<CopyModal {...props} />);

  expect(loadEntities).toHaveBeenCalled();
});

it('should offer to move the copy', () => {
  const node = shallow(<CopyModal {...props} jumpToEntity />);

  node.find(Switch).simulate('change', {target: {checked: true}});

  expect(node).toMatchSnapshot();
});

it('should hide option to move the copy for collection entities', () => {
  const node = shallow(
    <CopyModal {...props} entity={{name: 'collection', entityType: 'collection'}} />
  );

  expect(node).toMatchSnapshot();
});

it('should call the onConfirm action', () => {
  const node = shallow(<CopyModal {...props} jumpToEntity />);

  node.find('.confirm').simulate('click');

  expect(props.onConfirm).toHaveBeenCalledWith('Test Dashboard (copy)', true, false);
});

it('should hide the jump checkbox if jumpToEntity property is not added', () => {
  const node = shallow(<CopyModal {...props} />);

  expect(node.find('LabeledInput[type="checkbox"]')).not.toExist();

  node.find('.confirm').simulate('click');

  expect(props.onConfirm).toHaveBeenCalledWith('Test Dashboard (copy)', undefined, false);
});
