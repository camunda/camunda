/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {TextArea} from '@carbon/react';

import {useErrorHandling} from 'hooks';

import {storeLicense} from './service';

import License from './License';

jest.mock('./service', () => ({
  storeLicense: jest.fn().mockReturnValue({}),
}));

jest.mock('hooks', () => ({
  useErrorHandling: jest.fn(() => ({
    mightFail: jest.fn((data, cb) => cb(data)),
    error: false,
    resetError: jest.fn(),
  })),
}));

it('should store a new license', async () => {
  const node = shallow(<License />);

  node.find(TextArea).simulate('change', {target: {value: 'new license key'}});
  node.find('Form').simulate('submit', {preventDefault: jest.fn()});

  expect(storeLicense).toHaveBeenCalledWith('new license key');
});

it('should show an error on failure', async () => {
  useErrorHandling.mockReturnValueOnce({
    mightFail: jest.fn(),
    error: {message: 'error happened'},
    resetError: jest.fn(),
  });
  const node = shallow(<License error={{message: 'error happened'}} />);

  expect(node.find({kind: 'error'}).prop('subtitle')).toBe('error happened');
});
