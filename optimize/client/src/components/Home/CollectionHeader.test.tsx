/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import {EntityListEntity} from 'types';

import CollectionHeader from './CollectionHeader';

const collection = {
  id: 'aCollectionId',
  name: 'aCollectionName',
  owner: 'user_id',
  lastModifier: 'user_id',
  lastModified: '2021-01-01T00:00:00.000Z',
  created: '2021-01-01T00:00:00.000',
  currentUserRole: 'manager',
  data: {},
} as EntityListEntity;

const props = {
  collection,
  isLoading: false,
  onEditStart: jest.fn(),
  onCopy: jest.fn(),
  onDelete: jest.fn(),
};

it('should call onEdit when clicking the edit button', () => {
  const onEditStartSpy = jest.fn();
  const node = shallow(<CollectionHeader {...props} onEditStart={onEditStartSpy} />);

  node.find('ForwardRef(OverflowMenuItem)').at(0).simulate('click');

  expect(onEditStartSpy).toHaveBeenCalled();
});

it('should call onCopy when clicking the copy button', () => {
  const onCopySpy = jest.fn();
  const node = shallow(<CollectionHeader {...props} onCopy={onCopySpy} />);

  node.find('ForwardRef(OverflowMenuItem)').at(1).simulate('click');

  expect(onCopySpy).toHaveBeenCalledWith({...collection, entityType: 'collection'});
});

it('should call onDelete when clicking the delete button', () => {
  const onDeleteSpy = jest.fn();
  const node = shallow(<CollectionHeader {...props} onDelete={onDeleteSpy} />);

  node.find('ForwardRef(OverflowMenuItem)').at(2).simulate('click');

  expect(onDeleteSpy).toHaveBeenCalledWith({...collection, entityType: 'collection'});
});

it('should render the collection name', () => {
  const node = shallow(<CollectionHeader {...props} />);

  expect(node.find('.text').text()).toBe(collection.name);
});

it('should render the collection role', () => {
  const node = shallow(<CollectionHeader {...props} />);

  expect(node.find('Tag').dive().find('Text').dive().text()).toBe('Manager');
});

it('should render loading state', () => {
  const node = shallow(<CollectionHeader {...props} isLoading={true} collection={null} />);

  expect(node.find('SkeletonText')).toExist();
  expect(node.find('SkeletonIcon')).toExist();
  expect(node.find('TagSkeleton')).toExist();
});

it('should not render the overflow menu when the user is not a manager', () => {
  const node = shallow(
    <CollectionHeader {...props} collection={{...collection, currentUserRole: 'editor'}} />
  );

  expect(node.find('OverflowMenu')).not.toExist();
});
