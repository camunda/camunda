/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import OverviewWithProvider from './Overview';
import {Dropdown} from 'components';

const wrapper = shallow(<OverviewWithProvider />);
const Overview = wrapper.props().children.type.WrappedComponent;

const props = {
  store: {
    loading: false
  },
  filter: jest.fn()
};

it('should show a loading indicator', () => {
  const node = shallow(<Overview {...props} store={{loading: true}} />);

  expect(node.find('LoadingIndicator')).toExist();
});

it('should show create Report buttons', async () => {
  const node = shallow(<Overview {...props} />);

  expect(node.find('.createReport')).toExist();
});

it('should have a Dropdown with more creation options', async () => {
  const node = shallow(<Overview {...props} />);

  expect(node.find('.createReport').find(Dropdown)).toExist();
  expect(node.find('.createReport').find(Dropdown)).toMatchSnapshot();
});

it('Should invoke filter function on search input change', () => {
  const node = shallow(<Overview {...props} />);

  node.find('.searchInput').simulate('change', {target: {value: 'test'}});

  expect(props.filter).toHaveBeenCalledWith('test');
});

it('Should invoke filter function with empty string on form reset', async () => {
  const node = shallow(<Overview {...props} />);

  node.find('.searchClear').simulate('click');

  expect(props.filter).toHaveBeenCalledWith('');
});
