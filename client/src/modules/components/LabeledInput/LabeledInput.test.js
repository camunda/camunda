import React from 'react';
import {mount} from 'enzyme';

import LabeledInput from './LabeledInput';

it('should create a label with the provided id', () => {
  const node = mount(<LabeledInput id="someId" />);

  expect(node.find('input')).toHaveProp('id', 'someId');
  expect(node.find('label')).toHaveProp('htmlFor', 'someId');
});

it('should generate an id if none is provided', () => {
  const node = mount(<LabeledInput />);

  expect(node.find('input')).toHaveProp('id');
  expect(node.find('label')).toHaveProp('htmlFor');
});

it('should include the child content', () => {
  const node = mount(<LabeledInput>some child content</LabeledInput>);

  expect(node).toIncludeText('some child content');
});
