/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';
import {runLastEffect} from '__mocks__/react';
import {Dropdown} from 'components';

import OptionsList from './OptionsList';
import Typeahead from './Typeahead';

const props = {
  open: true,
  input: {
    addEventListener: jest.fn(),
  } as jest.MockedObject<HTMLInputElement>,
  filter: '',
  onSelect: jest.fn(),
  children: <div />,
  onOpen: jest.fn(),
  onRemove: jest.fn(),
  onClose: () => {},
};

it('should render an empty OptionsList', () => {
  const node = shallow(<OptionsList {...props} />);

  expect(node).toMatchSnapshot();
});

it('should attach keyPressEvent to input', () => {
  shallow(<OptionsList {...props} />);

  runLastEffect();

  expect(props.input.addEventListener).toHaveBeenCalled();
});

it('should filter option list options', () => {
  const node = shallow(
    <OptionsList {...props} filter="one">
      <Typeahead.Option id="test_option" value="1">
        One
      </Typeahead.Option>
      <Typeahead.Option id="second_option" value="2">
        Two
      </Typeahead.Option>
    </OptionsList>
  );

  expect(node.find('#second_option')).not.toExist();
});

it('should invoke onSelect when selecting an option', () => {
  const node = shallow(
    <OptionsList {...props}>
      <Typeahead.Option id="test_option" value="1">
        One
      </Typeahead.Option>
      <Typeahead.Option id="second_option" value="2">
        Two
      </Typeahead.Option>
    </OptionsList>
  );

  node.find('#second_option').simulate('click');

  expect(props.onSelect.mock.calls[0][0].props.value).toBe('2');
});

it('should render has more info message if specified', () => {
  const node = shallow(<OptionsList {...props} hasMore={true} />);

  expect(node).toMatchSnapshot();
});

it('should render an option with the search as input if optionList has typedOption prop and empty', () => {
  const node = shallow(<OptionsList {...props} filter="test" typedOption />);

  expect(node).toMatchSnapshot();
});

it('should not display an option if it is identical to the typed option', () => {
  const node = shallow(
    <OptionsList {...props} filter="test" typedOption>
      <Typeahead.Option id="test_option" value="1">
        Test 1
      </Typeahead.Option>
      <Typeahead.Option id="second_option" value="2">
        test
      </Typeahead.Option>
    </OptionsList>
  );

  expect(node.find(Dropdown.Option)).toHaveLength(2);
});

it('should not crash when not providing a mouseDown handler', () => {
  const node = shallow(
    <OptionsList {...props}>
      <Typeahead.Option id="test_option" value="1">
        Test 1
      </Typeahead.Option>
    </OptionsList>
  );

  node.find(Dropdown.Option).simulate('mousedown');
});

it('should not crash when providing null children', () => {
  const node = shallow(
    <OptionsList {...props}>
      <Typeahead.Option id="test_option" value="1">
        {null}
      </Typeahead.Option>
    </OptionsList>
  );

  node.find(Dropdown.Option).simulate('mousedown');
});
