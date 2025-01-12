/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';
import {ComboBox} from '@carbon/react';

import SingleUserInput from './SingleUserInput';
import useLoadIdentities from './useLoadIdentities';
import {User} from './service';

jest.mock('./service', () => ({
  identityToItem: jest.fn().mockReturnValue({id: 'user1'}),
  getItems: jest.fn().mockReturnValue([]),
  getSelectedIdentity: jest.fn().mockImplementation((id) => ({id})),
}));

jest.mock('./useLoadIdentities', () =>
  jest.fn().mockReturnValue({
    loading: false,
    identities: [],
    loadNewValues: jest.fn(),
  })
);

const mockUseLoadIdentities = useLoadIdentities as jest.Mock;

const props = {
  users: [{identity: {id: 'user1'}}] as User[],
  collectionUsers: [{identity: {id: 'user2'}}] as User[],
  onAdd: jest.fn(),
  fetchUsers: jest.fn(),
  optionsOnly: false,
  onClear: jest.fn(),
  excludeGroups: false,
  titleText: 'Select User',
  onRemove: jest.fn(),
};

afterEach(() => {
  jest.clearAllMocks();
});

it('should render without crashing', () => {
  const node = shallow(<SingleUserInput {...props} />);
  expect(node.exists()).toBe(true);
});

it('should render ComboBox with correct props', () => {
  const node = shallow(<SingleUserInput {...props} />);
  const comboBox = node.find(ComboBox);
  expect(comboBox.exists()).toBe(true);
  expect(comboBox.prop('titleText')).toEqual(props.titleText);
  expect(comboBox.prop('id')).toBeDefined();
  expect(comboBox.prop('selectedItem')).toEqual({id: 'user1'});
  expect(comboBox.prop('items')).toEqual([]);
});

it('should call handleInputChange on ComboBox input change', () => {
  const node = shallow(<SingleUserInput {...props} />);
  const inputText = 'test';
  node.find(ComboBox).prop('onInputChange')?.(inputText);
  const {loadNewValues} = mockUseLoadIdentities();
  expect(loadNewValues).toHaveBeenCalledWith(inputText, 800);
});

it('should call onAdd when selecting an identity', () => {
  const node = shallow(<SingleUserInput {...props} />);
  const selectedItem = {id: 'newUser'};
  node.find(ComboBox).prop('onChange')({selectedItem});

  expect(props.onAdd).toHaveBeenCalledWith(selectedItem);
});

it('should call onClear if selectedItem is null on ComboBox item select', () => {
  const node = shallow(<SingleUserInput {...props} />);
  node.find(ComboBox).prop('onChange')({selectedItem: null});
  expect(props.onClear).toHaveBeenCalled();
});

it('should not call onAdd if the same item is selected', () => {
  const node = shallow(<SingleUserInput {...props} />);
  const selectedItem = {id: 'user1'};
  node.find(ComboBox).prop('onChange')({selectedItem});

  expect(props.onAdd).not.toHaveBeenCalled();
});

it('should not call onAdd when loading is true', () => {
  mockUseLoadIdentities.mockReturnValueOnce({
    loading: true,
    identities: [],
    loadNewValues: jest.fn(),
  });
  const node = shallow(<SingleUserInput {...props} />);
  const selectedItem = {id: 'newUser'};

  node.find(ComboBox).prop('onChange')({selectedItem});
  expect(props.onAdd).not.toHaveBeenCalled();
});

it('should return the correct string representation of an item', () => {
  const items = [
    {id: '1', label: 'John Doe', subText: 'john@example.com'},
    {id: '2', label: '', subText: 'jane@example.com'},
    {id: '3', label: '', subText: ''},
  ];

  const node = shallow(<SingleUserInput {...props} />);

  expect(node.find(ComboBox).prop('itemToString')?.(items[0])).toBe('John Doe');
  expect(node.find(ComboBox).prop('itemToString')?.(items[1])).toBe('jane@example.com');
  expect(node.find(ComboBox).prop('itemToString')?.(items[2])).toBe('3');
  expect(node.find(ComboBox).prop('itemToString')?.(null)).toBe('');
});
