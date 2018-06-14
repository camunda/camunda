import React from 'react';
import {shallow} from 'enzyme';

import Dashboard from './Dashboard';
// import MetricTile from './MetricTile/MetricTile';

import * as api from './api';
import {mockResolvedAsyncFn} from 'modules/testUtils';

api.loadRunningInst = mockResolvedAsyncFn();
api.loadInstWithoutIncidents = mockResolvedAsyncFn();
api.loadInstWithIncidents = mockResolvedAsyncFn();

describe('<Dashboard/>', () => {
  it('should render <MetricPanel/> component', () => {
    const node = shallow(<Dashboard />);
    expect(node.find('MetricPanel').exists()).toBe(true);
  });

  it('should render three <MetricTile> components', () => {
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
// describe('<MetricTile>', () => {
//   it('should show the metric', () => {
//     const node = shallow(
//       <MetricTile metric={2} name="Active" metricColor="allIsWell" />
//     );

//     expect(node.find('styled.div').exists()).toBe(true);
//   });
// });
