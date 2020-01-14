/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import UserTypeahead from './UserTypeahead';
import {searchIdentities} from './service';

jest.mock('debounce', () =>
  jest.fn(fn => {
    fn.clear = jest.fn();
    return fn;
  })
);

jest.mock('./service', () => ({
  searchIdentities: jest.fn().mockReturnValue({result: [], total: 50})
}));

const props = {
  onChange: jest.fn()
};

it('should render a Typeahead', () => {
  const node = shallow(<UserTypeahead {...props} />);

  expect(node.find('Typeahead')).toExist();
});

it('should load initial data when component is mounted', () => {
  shallow(<UserTypeahead />);

  expect(searchIdentities).toHaveBeenCalledWith('');
});

it('should enable loading while loading data and enable hasMore if there are more data available', async () => {
  const node = shallow(<UserTypeahead {...props} />);

  expect(node.find('Typeahead').prop('loading')).toBe(true);
  await node.update();
  expect(node.find('Typeahead').prop('loading')).toBe(false);
  expect(node.find('Typeahead').prop('hasMore')).toBe(true);
});

it('should enable loading if typeahead is closed while empty', async () => {
  const node = shallow(<UserTypeahead />);

  node.find('Typeahead').prop('onSearch')('test');
  await node.update();
  node.find('Typeahead').prop('onClose')();
  expect(node.find('Typeahead').prop('loading')).toBe(true);
});

it('should format user list information correctly', () => {
  const node = shallow(<UserTypeahead {...props} />);

  node.setState({
    identities: [
      {id: 'testUser'},
      {id: 'user2', email: 'testUser@test.com'},
      {id: 'groupId', name: 'groupName', email: 'group@test.com', type: 'group'}
    ]
  });

  expect(node).toMatchSnapshot();
});
