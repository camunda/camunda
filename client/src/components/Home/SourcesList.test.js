/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {ConfirmationModal, Dropdown} from 'components';

import {removeSource} from './service';

import SourcesListWithErrorHandling from './SourcesList';

jest.mock('./service', () => ({
  getSources: jest.fn().mockReturnValue([
    {
      definitionType: 'PROCESS',
      definitionKey: 'defKey',
      definitionName: 'definition name',
      versions: ['1', '2'],
      tenants: [{id: null, name: 'Not defined'}, {id: 'tenant1', name: 'Sales'}]
    },
    {
      definitionType: 'DECISION',
      definitionKey: 'defKey2',
      definitionName: 'decision report',
      versions: ['ALL'],
      tenants: [{id: null, name: 'Not defined'}, {id: 'tenant1', name: 'Marketing'}]
    }
  ]),
  removeSource: jest.fn()
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

  node.setState({deleting: {definitionKey: 'defKey'}});
  node.find(ConfirmationModal).prop('onConfirm')();

  expect(removeSource).toHaveBeenCalledWith('collectionId', 'defKey');
});
