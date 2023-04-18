/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Input} from 'components';

import Typeahead from './Typeahead';

it('should render an empty Input', () => {
  const node = shallow(<Typeahead children={null} />);

  expect(node).toMatchSnapshot();
});

it('should show/hide options list on input focus/blur', () => {
  const node = shallow(
    <Typeahead initialValue="1">
      <Typeahead.Option id="test_option" value="1">
        Option One
      </Typeahead.Option>
    </Typeahead>
  );

  node.find(Input).simulate('focus');

  expect(node.find('OptionsList').prop('open')).toBe(true);

  node.find(Input).simulate('blur');

  expect(node.find('OptionsList').prop('open')).toBe(false);
  expect(node.find(Input).prop('value')).toBe('Option One');
});

it('should show option list on arrow button click', () => {
  const node = shallow<Typeahead>(
    <Typeahead>
      <Typeahead.Option id="test_option" value="1">
        Option One
      </Typeahead.Option>
    </Typeahead>
  );

  node.find('.optionsButton').simulate('click');

  expect(node.find('OptionsList')).toExist();
});

it('should select an option', () => {
  const spy = jest.fn();
  const node = shallow(
    <Typeahead onChange={spy}>
      <Typeahead.Option id="test_option" value="1">
        Option One
      </Typeahead.Option>
    </Typeahead>
  );

  node.find(Input).simulate('focus');

  node.find('OptionsList').simulate('select', {props: {children: 'Option One', value: '1'}});

  expect(node.find(Input).prop('value')).toBe('Option One');
  expect(node.find('OptionsList').prop('open')).toBe(false);
  expect(spy).toHaveBeenCalledWith('1');
});

it('should function as a controlled select', () => {
  const node = shallow(
    <Typeahead>
      <Typeahead.Option id="test_option" value="1">
        Option One
      </Typeahead.Option>
    </Typeahead>
  );

  node.setProps({value: '1'});

  expect(node.find(Input).prop('value')).toBe('Option One');
});

it('should always select value if typedOption is set', () => {
  const node = shallow(<Typeahead value="typed" typedOption children={null} />);

  expect(node.find(Input).prop('value')).toBe('typed');
});
