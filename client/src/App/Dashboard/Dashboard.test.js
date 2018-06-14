import React from 'react';
import {shallow} from 'enzyme';

import Dashboard from './Dashboard';
import MetricTile from './MetricTile/MetricTile';

import * as Styled from './MetricTile/styled';

import * as api from './api';
import {mockResolvedAsyncFn} from 'modules/testUtils';

api.loadRunningInst = mockResolvedAsyncFn(3);
api.loadInstWithoutIncidents = mockResolvedAsyncFn(2);
api.loadInstWithIncidents = mockResolvedAsyncFn(1);

describe('<Dashboard/>', () => {
  it('should render <MetricPanel/> component', () => {
    // const node = shallow(<Dashboard />);
    // expect(node.find('MetricPanel').exists()).toBe(true);
  });

  it('should render three <MetricTile> components', async () => {
    const node = shallow(<Dashboard />);
    expect(node.find('MetricPanel').children().length).toBe(3);
  });

  it('should request running instance metrics', async () => {
    const node = shallow(<Dashboard />);
    await node.instance().componentDidMount();
    expect(api.loadRunningInst).toHaveBeenCalled();
  });

  it('should request active instance metrics', async () => {
    const node = shallow(<Dashboard />);
    await node.instance().componentDidMount();
    expect(api.loadInstWithoutIncidents).toHaveBeenCalled();
  });

  it('should request with incidents metrics', async () => {
    const node = shallow(<Dashboard />);
    await node.instance().componentDidMount();
    expect(api.loadInstWithIncidents).toHaveBeenCalled();
  });
});

describe('<MetricTile>', () => {
  it('should show the passed metric value', () => {
    const node = shallow(
      <MetricTile metric={123} name="Active" metricColor="allIsWell" />
    );
    expect(node.find(Styled.Metric).contains(123)).toEqual(true);
  });

  it('should show the passed metric name', () => {
    const node = shallow(
      <MetricTile metric={123} name="Active" metricColor="allIsWell" />
    );
    expect(node.find(Styled.Name).contains('Active')).toEqual(true);
  });

  it('should show styled metric when "allIsWell" color is passed', () => {
    const node = shallow(
      <MetricTile metric={2} name="Active" metricColor="allIsWell" />
    );
    expect(node.find(Styled.Metric)).toHaveLength(1);
  });

  it('should show styled metric when "incidentsAndErrors" color is passed', () => {
    const node = shallow(
      <MetricTile metric={2} name="Active" metricColor="incidentsAndErrors" />
    );
    expect(node.find(Styled.Metric)).toHaveLength(1);
  });

  it('should show themed metric when "themed" color is passed', () => {
    const node = shallow(
      <MetricTile metric={2} name="Active" metricColor="themed" />
    );
    expect(node.find(Styled.ThemedMetric)).toHaveLength(1);
  });
});
