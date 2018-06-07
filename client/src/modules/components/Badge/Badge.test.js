import React from 'react';
import {mount} from 'enzyme';

import Badge from './Badge';

it('should contain the child content', () => {
  const node = mount(<Badge>content</Badge>);

  expect(node).toIncludeText('content');
});
