import React from 'react';
import {mount} from 'enzyme';

import Labeled from './Labeled';

it('should create a label with the provided id', () => {
  const node = mount(
    <Labeled id="someId">
      <div className="test" />
    </Labeled>
  );

  expect(node.find('div.test')).toHaveProp('id', 'someId');
  expect(node.find('label')).toHaveProp('htmlFor', 'someId');
});

it('should generate an id if none is provided', () => {
  const node = mount(
    <Labeled>
      <div className="test" />
    </Labeled>
  );

  expect(node.find('div.test')).toHaveProp('id');
  expect(node.find('label')).toHaveProp('htmlFor');
});

it('should include the child content', () => {
  const node = mount(
    <Labeled id="someId">
      <div>some child content</div>
    </Labeled>
  );

  expect(node).toIncludeText('some child content');
});
