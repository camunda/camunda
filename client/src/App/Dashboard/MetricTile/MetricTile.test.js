import React from 'react';
import {mount} from 'enzyme';

import {HashRouter as Router} from 'react-router-dom';
import {ThemeProvider} from 'modules/contexts/ThemeContext';

import MetricTile from './MetricTile';

import * as Styled from './styled';

const mockDefaultProps = {
  type: 'active',
  value: 123,
  label: 'Active',
  expandFilters: jest.fn()
};

const mountNode = mockCustomProps => {
  return mount(
    <Router>
      <ThemeProvider>
        <MetricTile {...mockDefaultProps} {...mockCustomProps} />
      </ThemeProvider>
    </Router>
  );
};

describe('<MetricTile>', () => {
  let node;
  let metricTileNode;

  beforeEach(() => {
    node = mountNode();
    metricTileNode = node.find(Styled.MetricTile);
  });

  it('should mount the tile', () => {
    expect(node.find(Styled.MetricTile).prop('onClick')).toBe(
      mockDefaultProps.expandFilters
    );
    expect(node.find(Styled.Metric).contains(123)).toEqual(true);
    expect(node.find(Styled.Label).contains('Active')).toEqual(true);
  });

  it('should show the value and name prop', () => {
    expect(node.find(Styled.MetricTile).prop('onClick')).toBe(
      mockDefaultProps.expandFilters
    );
    expect(node.find(Styled.Metric).contains(123)).toEqual(true);
    expect(node.find(Styled.Label).contains('Active')).toEqual(true);
  });

  it('should contain a link to instances view', () => {
    expect(metricTileNode.props().to).toContain('/instances');
  });

  it('should return a link with the active filters in place', () => {
    expect(metricTileNode.props().to).toEqual(
      '/instances?filter={"active":true}'
    );
    expect(metricTileNode.props().title).toEqual('View 123 Active');
  });

  it('should return a link with the running filters in place', () => {
    const node = mountNode({type: 'running', label: 'Running'});
    const metricTileNode = node.find(Styled.MetricTile);
    expect(metricTileNode.props().to).toEqual(
      '/instances?filter={"active":true,"incidents":true}'
    );
    expect(metricTileNode.props().title).toEqual('View 123 Running');
  });

  it('should return a link with the incidents filters in place', () => {
    const node = mountNode({type: 'incidents', label: 'Incidents'});
    const metricTileNode = node.find(Styled.MetricTile);

    expect(metricTileNode.props().to).toEqual(
      '/instances?filter={"incidents":true}'
    );
    expect(metricTileNode.props().title).toEqual('View 123 Incidents');
  });
});
