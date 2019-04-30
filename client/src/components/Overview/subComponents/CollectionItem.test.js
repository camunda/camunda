/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import CollectionItem from './CollectionItem';
import {getReportIcon} from '../service';

jest.mock('../service');

const processReport = {
  id: 'reportID',
  name: 'Some Report',
  lastModifier: 'Admin',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reportType: 'process',
  combined: false
};

const collection = {
  id: 'aCollectionId',
  name: 'aCollectionName',
  created: '2017-11-11T11:11:11.1111+0200',
  owner: 'user_id',
  data: {
    configuration: {},
    entities: [processReport]
  }
};

beforeAll(() => {
  getReportIcon.mockReturnValue({Icon: () => {}, label: 'Icon'});
});

const props = {
  collection,
  opened: false,
  setCollectionToUpdate: jest.fn(),
  showDeleteModalFor: jest.fn(),
  toggleOpened: jest.fn()
};

it('should show information about collections', () => {
  const node = shallow(<CollectionItem {...props} />);

  expect(node.find('.dataTitle')).toIncludeText('aCollectionName');
});

it('should invok setCollectionToUpdate on updating a collection', () => {
  const node = shallow(<CollectionItem {...props} />);
  node
    .find('.operations')
    .find(Button)
    .first()
    .simulate('click');

  expect(props.setCollectionToUpdate).toHaveBeenCalledWith({
    id: 'aCollectionId',
    name: 'aCollectionName',
    data: {entities: ['reportID']}
  });
});

it('should contain a button to collapse the entities list', () => {
  const node = shallow(<CollectionItem {...props} />);

  expect(node.find('.ToggleCollapse')).toExist();
});

it('should toggle expanded when clicking the collapse button', () => {
  const node = shallow(<CollectionItem {...props} />);

  node.find('.ToggleCollapse').simulate('click');

  expect(node.state().expanded).toBeTruthy();
});

it('should render children component if item is opened', () => {
  const node = shallow(
    <CollectionItem {...props}>
      <div className="test" />
    </CollectionItem>
  );

  node.setState({expanded: true});
  expect(node.find('.test')).toExist();
});
