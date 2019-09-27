/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Dropdown} from 'components';
import {checkDeleteConflict, deleteEntity} from 'services';

import CreateNewButton from './CreateNewButton';

import EntityListWithErrorHandling from './EntityList';

const EntityList = EntityListWithErrorHandling.WrappedComponent;

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    checkDeleteConflict: jest.fn().mockReturnValue([]),
    deleteEntity: jest.fn()
  };
});

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  data: [
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
  ]
};

it('should match snapshot', () => {
  const node = shallow(<EntityList {...props} />);

  expect(node).toMatchSnapshot();
});

it('should show a loading indicator', () => {
  const node = shallow(<EntityList mightFail={() => {}} data={null} />);

  expect(node.find('LoadingIndicator')).toExist();
});

it('should show an empty message if no entities exist', () => {
  const node = shallow(<EntityList {...props} data={[]} />);

  expect(node.find('.empty')).toExist();
});

it('should show a confirm delete modal when clicking delete in context menu', () => {
  const node = shallow(<EntityList {...props} />);

  node
    .find(Dropdown.Option)
    .last()
    .simulate('click');

  expect(node.find('ConfirmationModal').prop('open')).toBeTruthy();
});

it('should check delete conflicts', () => {
  const node = shallow(<EntityList {...props} />);

  node
    .find(Dropdown.Option)
    .last()
    .simulate('click');

  expect(checkDeleteConflict).toHaveBeenCalledWith('aReportId', 'report');
});

it('should perform a delete and hide the modal afterwards', () => {
  const node = shallow(<EntityList {...props} />);

  node
    .find(Dropdown.Option)
    .last()
    .simulate('click');

  node.find('ConfirmationModal').prop('onConfirm')();

  expect(deleteEntity).toHaveBeenCalledWith('report', 'aReportId');
  expect(node.find('ConfirmationModal').prop('open')).toBeFalsy();
});

it('should filter results based on search input', () => {
  const node = shallow(<EntityList {...props} />);
  node.find('.searchInput').simulate('change', {target: {value: 'adashboard'}});

  expect(node.find('ListItem').length).toBe(1);
});

it('should show no result found text when no matching entities were found', () => {
  const node = shallow(<EntityList {...props} />);

  node.find('.searchInput').simulate('change', {target: {value: 'not found entity'}});

  expect(node.find('.empty')).toIncludeText('No results found');
});

it('should hide edit/delete from context menu for collection items that does not have a "manager" role', () => {
  const node = shallow(
    <EntityList
      {...props}
      data={[
        {
          id: 'aCollectionId',
          name: 'aCollectionName',
          lastModified: '2017-11-11T11:11:11.1111',
          created: '2017-11-11T11:11:11.1111',
          owner: 'user_id',
          lastModifier: 'user_id',
          entityType: 'collection',
          currentUserRole: 'editor',
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
        }
      ]}
    />
  );

  expect(node.find('.contextMenu').find({type: 'delete'})).not.toExist();
  expect(node.find('.contextMenu').find({type: 'edit'})).not.toExist();
});

it('should hide edit/delete from context menu for report/dashboard items that does not have a "editor" role', () => {
  const node = shallow(
    <EntityList
      {...props}
      data={[
        {
          id: 'aReportId',
          name: 'aReport',
          lastModified: '2017-11-11T11:11:11.1111',
          created: '2017-11-11T11:11:11.1111',
          owner: 'user_id',
          currentUserRole: 'viewer',
          data: {subEntityCounts: {}, roleCounts: {}},
          lastModifier: 'user_id',
          reportType: 'process', // or 'decision'
          combined: false,
          entityType: 'report'
        }
      ]}
    />
  );

  expect(node.find('.contextMenu').find({type: 'delete'})).not.toExist();
  expect(node.find('.contextMenu').find({type: 'edit'})).not.toExist();
});

it('should hide the create new button if it is in readonly mode', () => {
  const node = shallow(<EntityList {...props} readOnly />);

  expect(node.find(CreateNewButton)).not.toExist();
});
