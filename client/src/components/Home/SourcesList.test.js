/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {ConfirmationModal, Dropdown} from 'components';

import {removeSource, editSource, addSources} from './service';

import SourcesListWithErrorHandling from './SourcesList';
import EditSourceModal from './modals/EditSourceModal';
import AddSourceModal from './modals/AddSourceModal';

jest.mock('./service', () => ({
  getSources: jest.fn().mockReturnValue([
    {
      id: 'process:defKey',
      definitionType: 'process',
      definitionKey: 'defKey',
      definitionName: 'definition name',
      tenants: [
        {id: null, name: 'Not defined'},
        {id: 'tenant1', name: 'Sales'}
      ]
    },
    {
      id: 'decision:defKey2',
      definitionType: 'decision',
      definitionKey: 'defKey2',
      definitionName: 'decision report',
      tenants: [
        {id: null, name: 'Not defined'},
        {id: 'tenant1', name: 'Marketing'}
      ]
    }
  ]),
  removeSource: jest.fn(),
  editSource: jest.fn(),
  addSources: jest.fn()
}));

const SourcesList = SourcesListWithErrorHandling.WrappedComponent;

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  collection: 'collectionId',
  readOnly: false
};

it('should match snapshot', () => {
  const node = shallow(<SourcesList {...props} />);

  expect(node).toMatchSnapshot();
});

it('should hide add button and edit menu when in readOnly mode', () => {
  const node = shallow(<SourcesList {...props} readOnly />);

  expect(node).toMatchSnapshot();
});

it('should show delete modal when clicking delete button', () => {
  const node = shallow(<SourcesList {...props} />);

  node
    .find(Dropdown.Option)
    .at(1)
    .simulate('click');

  expect(node.find(ConfirmationModal).prop('open')).toBeTruthy();
});

it('should delete source', () => {
  const node = shallow(<SourcesList {...props} />);

  node.setState({deleting: {id: 'sourceId'}});

  node.find(ConfirmationModal).prop('onConfirm')();

  expect(removeSource).toHaveBeenCalledWith('collectionId', 'sourceId');
});

it('should show an edit modal when clicking the edit button', () => {
  const node = shallow(<SourcesList {...props} />);

  node
    .find(Dropdown.Option)
    .at(0)
    .simulate('click');

  expect(node.find(EditSourceModal)).toExist();
});

it('should modify the tenants in source', () => {
  const node = shallow(<SourcesList {...props} />);

  node.setState({editing: {id: 'sourceId'}});
  const updatedTenants = [{id: 'newTenant', name: 'New Tenant'}];
  node.find(EditSourceModal).prop('onConfirm')(updatedTenants);

  expect(editSource).toHaveBeenCalledWith('collectionId', 'sourceId', updatedTenants);
});

it('should add sources when addSourceModal is confirmed', () => {
  const node = shallow(<SourcesList {...props} />);

  node.setState({addingSource: true});
  const sources = [
    {
      id: 'sourceId',
      definitionType: 'process',
      definitionKey: 'defKey',
      tenants: ['tenantId']
    }
  ];
  node.find(AddSourceModal).prop('onConfirm')(sources);

  expect(addSources).toHaveBeenCalledWith('collectionId', sources);
});
