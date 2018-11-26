import React from 'react';
import {shallow} from 'enzyme';

import MetricTile from './MetricTile';

import * as Styled from './styled';

const mockProps = {
  type: 'active',
  value: 123,
  label: 'Active',
  expandFilters: jest.fn()
};

describe('<MetricTile>', () => {
  it('should show the value and name prop', () => {
    const node = shallow(<MetricTile {...mockProps} />);
    expect(node.find(Styled.MetricTile).prop('onClick')).toBe(
      mockProps.expandFilters
    );
    expect(node.find(Styled.Metric).contains(123)).toEqual(true);
    expect(node.find(Styled.Label).contains('Active')).toEqual(true);
  });

  it('should contain a link to instances view', () => {
    const node = shallow(<MetricTile {...mockProps} />);
    expect(node.props().to).toContain('/instances');
  });

  it('should return a link with the active filters in place', () => {
    const node = shallow(<MetricTile {...mockProps} />);
    expect(node.props().to).toEqual('/instances?filter={"active":true}');
    expect(node.props().title).toEqual('View 123 Active');
  });

  it('should return a link with the running filters in place', () => {
    const node = shallow(
      <MetricTile {...mockProps} type="running" label="Running" />
    );
    expect(node.props().to).toEqual(
      '/instances?filter={"active":true,"incidents":true}'
    );
    expect(node.props().title).toEqual('View 123 Running');
  });

  it('should return a link with the incidents filters in place', () => {
    const node = shallow(
      <MetricTile {...mockProps} type="incidents" label="Incidents" />
    );
    expect(node.props().to).toEqual('/instances?filter={"incidents":true}');
    expect(node.props().title).toEqual('View 123 Incidents');
  });
});
