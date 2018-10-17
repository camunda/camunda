import React from 'react';
import {mount} from 'enzyme';

import Labeled from './Labeled';
it('should pass props to label child element', () => {
  const node = mount(
    <Labeled id="someId">
      <div className="test" />
    </Labeled>
  );

  expect(node.find('label')).toHaveProp('id', 'someId');
});
it('should include the child content', () => {
  const node = mount(
    <Labeled id="someId">
      <div>some child content</div>
      <div>test</div>
    </Labeled>
  );

  expect(node).toIncludeText('some child content');
});
