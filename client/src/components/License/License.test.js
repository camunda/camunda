/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {storeLicense} from './service';

import {License} from './License';

jest.mock('./service', () => ({
  storeLicense: jest.fn().mockReturnValue({}),
}));

it('should store a new license', async () => {
  const spy = jest.fn();
  const node = shallow(
    <License mightFail={jest.fn().mockImplementation((data, cb) => cb(data))} resetError={spy} />
  );

  node.find('textarea').simulate('change', {target: {value: 'new license key'}});
  node.find('Form').simulate('submit', {preventDefault: jest.fn()});

  expect(storeLicense).toHaveBeenCalledWith('new license key');
  expect(spy).toHaveBeenCalled();
});

it('should show an error on failure', async () => {
  const node = shallow(<License mightFail={jest.fn()} error={{message: 'error happened'}} />);

  expect(node.find({type: 'error'}).children()).toIncludeText('error happened');
});
