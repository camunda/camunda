/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ItemsList from './ItemsList';

const props = {
  selectedItems: [{id: 'item1'}],
  allItems: [{id: 'item1'}, {id: 'item2'}],
  onChange: jest.fn(),
  formatter: jest.fn()
};

it('should call the formatter with the list items data', () => {
  shallow(<ItemsList {...props} />);

  expect(props.formatter).toHaveBeenCalledWith(props.allItems, props.selectedItems);
});

it('should invoke onChange with the updated selected items', () => {
  const node = shallow(<ItemsList {...props} />);

  node
    .find('Checklist')
    .props()
    .onChange('item1', false);

  expect(props.onChange).toHaveBeenCalledWith([]);

  node
    .find('Checklist')
    .props()
    .onChange('item2', true);

  expect(props.onChange).toHaveBeenCalledWith([{id: 'item1'}, {id: 'item2'}]);
});

it('should invoke onChange on selectAll/deselectAll', () => {
  const node = shallow(<ItemsList {...props} />);

  node
    .find('Checklist')
    .props()
    .selectAll();

  expect(props.onChange).toHaveBeenCalledWith(props.allItems);

  node
    .find('Checklist')
    .props()
    .deselectAll();

  expect(props.onChange).toHaveBeenCalledWith([]);
});
