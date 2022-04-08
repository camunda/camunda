/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ProgressBar from './ProgressBar';

const props = {
  min: 0,
  max: 100,
  value: 20,
  formatter: () => 'formatted',
};

it('should use the provided formatter', () => {
  const node = shallow(<ProgressBar {...props} />);

  expect(node).toIncludeText('formatted');
});

it('should fill according to the provided value, min and max properties', () => {
  const node = shallow(<ProgressBar {...props} />);

  expect(node.find('.filledOverlay')).toHaveProp('style', {width: '20%'});
});

it('should show the overlay with the goal value when the goal value is exceeded', () => {
  const props = {
    min: 0,
    max: 100,
    value: 150,
    formatter: () => 'formatted',
  };

  const node = shallow(<ProgressBar {...props} />);

  expect(node.find('.goalOverlay')).toIncludeText('Goal');
});

it('should show an invalid configuration message if min or max props are invalid', () => {
  const node1 = shallow(<ProgressBar min={500} max={1} value={100} formatter={() => {}} />);
  const node2 = shallow(<ProgressBar min="five" max={100} value={100} formatter={() => {}} />);
  const node3 = shallow(<ProgressBar min="0" max="1 Thousand" value={100} formatter={() => {}} />);

  expect(node1).toIncludeText('Invalid Configuration');
  expect(node2).toIncludeText('Invalid Configuration');
  expect(node3).toIncludeText('Invalid Configuration');
});
