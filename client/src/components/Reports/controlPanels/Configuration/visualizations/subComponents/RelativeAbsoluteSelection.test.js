/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import RelativeAbsoluteSelection from './RelativeAbsoluteSelection';

const props = {
  absolute: true,
  relative: true,
  reportType: 'process',
};

it('should match snapshot', () => {
  const node = shallow(<RelativeAbsoluteSelection {...props} />);

  expect(node).toMatchSnapshot();
});

it('should call the onChange method with the correct prop and value', () => {
  const spy = jest.fn();
  const node = shallow(<RelativeAbsoluteSelection {...props} onChange={spy} />);

  node
    .find('Switch')
    .at(0)
    .simulate('change', {target: {checked: false}});

  expect(spy).toHaveBeenCalledWith('absolute', false);
});

it('hide the relative selection when hideRelative is true', () => {
  const node = shallow(<RelativeAbsoluteSelection {...props} hideRelative />);

  expect(node).toMatchSnapshot();
});
