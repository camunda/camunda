import React from 'react';
import {shallow} from 'enzyme';

import AggregationType from './AggregationType';

it('should render nothing if the current result does is no duration', () => {
  const node = shallow(<AggregationType report={{resultType: 'rawData'}} />);

  expect(node).toMatchSnapshot();
});

it('should render an aggregation selection for duration reports', () => {
  const node = shallow(
    <AggregationType
      report={{resultType: 'durationMap', data: {configuration: {aggregationType: 'median'}}}}
    />
  );

  expect(node).toMatchSnapshot();
});

it('should not crash when no resultType is set (e.g. for combined reports)', () => {
  shallow(<AggregationType report={{}} />);
});
