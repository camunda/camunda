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
    expect(node.find(Styled.Label).contains('Active')).toEqual(true);
  });

  it('should contain a link to instances view', () => {
    const node = shallow(
      <MetricTile type="active" value={123} label="Active" />
    );
    expect(node.props().to).toContain('/instances');
  });

  it('should return a link with the active filters in place', () => {
    const node = shallow(
      <MetricTile type="active" value={123} label="Active" />
    );
    expect(node.props().to).toEqual(
      `/instances?filter=${encodeURIComponent('{"active":true}')}`
    );
  });

  it('should return a link with the running filters in place', () => {
    const node = shallow(
      <MetricTile type="running" value={123} label="Running" />
    );
    expect(node.props().to).toEqual(
      `/instances?filter=${encodeURIComponent(
        '{"active":true,"incidents":true}'
      )}`
    );
  });

  it('should return a link with the incidents filters in place', () => {
    const node = shallow(
      <MetricTile type="incidents" value={123} label="Incidents" />
    );
    expect(node.props().to).toEqual(
      `/instances?filter=${encodeURIComponent('{"incidents":true}')}`
    );
  });
});
