/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {isEventBasedProcessEnabled} from './service';

import {Header} from './Header';

jest.mock('config', () => ({
  getHeader: jest.fn().mockReturnValue({
    textColor: 'light',
    backgroundColor: '#000',
    logo: 'url',
  }),
}));

jest.mock('./service', () => ({
  isEventBasedProcessEnabled: jest.fn().mockReturnValue(true),
}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  location: {pathname: '/'},
  history: {push: jest.fn()},
};

it('matches the snapshot', async () => {
  const node = shallow(<Header {...props} />);

  await runLastEffect();

  expect(node).toMatchSnapshot();
});

it('should check if the event based process feature is enabled', async () => {
  isEventBasedProcessEnabled.mockClear();
  shallow(<Header {...props} />);

  await runLastEffect();

  expect(isEventBasedProcessEnabled).toHaveBeenCalled();
});

it('should show and hide the event based process nav item depending on authorization', async () => {
  isEventBasedProcessEnabled.mockReturnValueOnce(true);
  const enabled = shallow(<Header {...props} />);

  await runLastEffect();

  expect(enabled.find('[linksTo="/events/processes/"]')).toExist();

  isEventBasedProcessEnabled.mockReturnValueOnce(false);
  const disabled = shallow(<Header {...props} />);

  await runLastEffect();

  expect(disabled.find('[linksTo="/eventBasedProcess/"]')).not.toExist();
});
