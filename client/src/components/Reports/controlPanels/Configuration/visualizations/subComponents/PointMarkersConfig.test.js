/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
    .simulate('change', {target: {checked: true}});
  expect(spy).toHaveBeenCalledWith({pointMarkers: {$set: false}});
});
