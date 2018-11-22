import React from 'react';
import {shallow} from 'enzyme';
import PointMarkersConfig from './PointMarkersConfig';

it('should invok onchange when changing switch for the point markers on line chart', () => {
  const spy = jest.fn();
  const node = shallow(<PointMarkersConfig configuration={{pointMarkers: true}} onChange={spy} />);
  node
    .find('Switch')
    .first()
    .simulate('change', {target: {checked: true}});
  expect(spy).toHaveBeenCalledWith('pointMarkers', false);
});
