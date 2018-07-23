import React from 'react';
import {shallow} from 'enzyme';

import Badge from './Badge';

describe('Badge', () => {
  it('should contain passed number', () => {
    const node = shallow(<Badge type={'filters'} badgeContent={123} />);
    expect(node.contains(123));
  });
  it('should show combo badge styles', () => {
    const node = shallow(
      <Badge type={'comboSelection'} circleContent={10} badgeContent={123} />
    );
    expect(node).toMatchSnapshot();
  });
});
