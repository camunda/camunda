/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {storeLicense} from './service';

import {License} from './License';

jest.mock('./service', () => ({
  storeLicense: jest.fn().mockReturnValue({}),
}));

it('should store a new license', async () => {
  const node = shallow(
    <License mightFail={jest.fn().mockImplementation((data, cb) => cb(data))} />
  );

  node.find('textarea').simulate('change', {target: {value: 'new license key'}});
  node.find('Form').simulate('submit', {preventDefault: jest.fn()});

  expect(storeLicense).toHaveBeenCalledWith('new license key');
});
