import React from 'react';
import {shallow} from 'enzyme';

import Badge from './Badge';

describe('Badge', () => {
  it('should match snapshot', () => {
    const node = shallow(
      <Badge className="shouldBePassedThrough">child content</Badge>
    );

    expect(node).toMatchSnapshot();
  });
});
