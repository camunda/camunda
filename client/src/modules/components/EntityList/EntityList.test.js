/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import EntityList from './EntityList';

const props = {
  name: 'EntityList Name',
  empty: 'Empty Message',
  children: <div>Some additional Content</div>,
  action: <button>Click Me</button>,
  data: [
    {
      name: 'aCollectionName',
      meta1: 'Some info',
      meta2: 'Some additional info',
      meta3: 'Some other info',
      icon: <p>An Image</p>,
      type: 'Collection',
      actions: [{icon: 'edit', text: 'Edit', action: jest.fn()}]
    },
    {
      name: 'aDashboard',
      meta1: 'Some info',
      meta2: 'Some additional info',
      meta3: 'Some other info',
      icon: <p>An Image</p>,
      type: 'Dashboard'
    },
    {
      name: 'aReport',
      meta1: 'Some info',
      meta2: 'Some additional info',
      meta3: 'Some other info',
      icon: <p>An Image</p>,
      type: 'Report',
      link: 'link/to/somewhere'
    }
  ]
};

it('should match snapshot', () => {
  const node = shallow(<EntityList {...props} />);

  expect(node).toMatchSnapshot();
});

it('should show a loading indicator', () => {
  const node = shallow(<EntityList isLoading />);

  expect(node.find('LoadingIndicator')).toExist();
});

it('should show an empty message if no entities exist', () => {
  const node = shallow(<EntityList {...props} data={[]} />);

  expect(node.find('.empty')).toExist();
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
