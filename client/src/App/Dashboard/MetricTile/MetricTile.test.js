import React from 'react';
import {shallow} from 'enzyme';

import MetricTile from './MetricTile';

import * as Styled from './styled';

describe('<MetricTile>', () => {
  it('should show the value and name prop', () => {
    const node = shallow(
      <MetricTile type="active" value={123} label="Active" />
    );
    expect(node.find(Styled.Metric).contains(123)).toEqual(true);
    expect(node.find(Styled.Name).contains('Active')).toEqual(true);
  });
});
