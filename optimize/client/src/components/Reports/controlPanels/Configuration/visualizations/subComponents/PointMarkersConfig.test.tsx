/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import PointMarkersConfig from './PointMarkersConfig';

it('should invok onchange when changing switch for the point markers on line chart', () => {
  const spy = jest.fn();
  const node = shallow(<PointMarkersConfig configuration={{pointMarkers: true}} onChange={spy} />);
  node.find('Toggle').first().simulate('toggle', false);
  expect(spy).toHaveBeenCalledWith({pointMarkers: {$set: false}});
});
