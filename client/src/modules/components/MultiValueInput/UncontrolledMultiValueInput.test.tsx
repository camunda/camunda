/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';

import UncontrolledMultiValueInput from './UncontrolledMultiValueInput';

const props = {
  value: '',
  onClear: jest.fn(),
  onRemove: jest.fn(),
};

beforeEach(() => {
  jest.clearAllMocks();
});

it('should match snapshot', () => {
  const node = shallow(
    <UncontrolledMultiValueInput
      {...props}
      values={[
        {value: '1234', label: '1234 label', invalid: false},
        {value: 'errorValue', invalid: true},
      ]}
    />
  );

  expect(node).toMatchSnapshot();
});

it('should show placeholder when empty', () => {
  const placeholderText = 'placeholderText';
  const node = shallow(
    <UncontrolledMultiValueInput {...props} values={[]} placeholder={placeholderText} />
  );

  expect(node.find('.placeholder')).toIncludeText(placeholderText);
});

it('should invoke onRemove when removing a value', async () => {
  const node = shallow(
    <UncontrolledMultiValueInput {...props} values={[{value: 'test1'}, {value: 'test2'}]} />
  );

  node.find('Tag').at(0).simulate('Remove');

  expect(props.onRemove).toHaveBeenCalledWith('test1', 0);
});
