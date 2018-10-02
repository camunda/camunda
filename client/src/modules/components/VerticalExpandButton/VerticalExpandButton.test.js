import React from 'react';
import {shallow} from 'enzyme';

import VerticalExpandButton from './VerticalExpandButton';

describe('VerticalExpandButton', () => {
  it('should render a button', () => {
    const label = 'Random label';
    const children = <button>Child content</button>;
    const node = shallow(
      <VerticalExpandButton label={label}>{children}</VerticalExpandButton>
    );

    expect(node.props().title).toBe(`Expand ${label}`);

    expect(node).toMatchSnapshot();
  });
});
