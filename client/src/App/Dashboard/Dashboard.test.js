import React from 'react';
import {shallow} from 'enzyme';
import {mockResolvedAsyncFn} from 'modules/testUtils';

import Dashboard from './Dashboard';

import * as api from './api';

api.fetchInstancesCount = mockResolvedAsyncFn(123);

describe('Dashboard', () => {
  it('should render MetricPanel component', () => {
    const node = shallow(<Dashboard />);
    expect(node.find('MetricPanel').exists()).toBe(true);
  });

  it('should render Header component', () => {
    const node = shallow(<Dashboard />);
    expect(node.find('Header').exists()).toBe(true);
  });

  it('should render three MetricTile components', async () => {
    const node = shallow(<Dashboard />);
    expect(node.find('MetricPanel').children().length).toBe(3);
  });

  it('it should request instance counts ', async () => {
    // given
    const node = shallow(<Dashboard />);
    const spyFetch = jest.spyOn(node.instance(), 'fetchCounts');

    // then
    await node.instance().componentDidMount();
    expect(spyFetch).toHaveBeenCalled();
  });
});
