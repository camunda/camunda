/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import PointMarkersConfig from './PointMarkersConfig';

it('should invok onchange when changing switch for the point markers on line chart', () => {
  const spy = jest.fn();
  const node = shallow(<PointMarkersConfig configuration={{pointMarkers: true}} onChange={spy} />);
  node
    .find('Switch')
    .first()
    .simulate('change', {target: {checked: false}});
  expect(spy).toHaveBeenCalledWith({pointMarkers: {$set: false}});
});
