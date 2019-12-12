/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {isEventBasedProcessEnabled} from './service';

import HeaderWithErrorHandling from './Header';

const Header = HeaderWithErrorHandling.WrappedComponent;

jest.mock('react-router-dom', () => {
  return {
    Link: ({children, to}) => {
      return <a href={to}>{children}</a>;
    },
    withRouter: fn => fn
  };
});

jest.mock('config', () => ({
  getHeader: jest.fn().mockReturnValue({
    textColor: 'light',
    backgroundColor: '#000',
    logo: 'url'
  })
}));

jest.mock('./service', () => ({
  isEventBasedProcessEnabled: jest.fn().mockReturnValue(true)
}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  name: 'Awesome App',
  location: {pathname: '/'}
};

it('matches the snapshot', () => {
  const node = shallow(<Header {...props} />);

  expect(node).toMatchSnapshot();
});

it('should check if the event based process feature is enabled', () => {
  isEventBasedProcessEnabled.mockClear();
  shallow(<Header {...props} />);

  expect(isEventBasedProcessEnabled).toHaveBeenCalled();
});

it('should show and hide the event based process nav item depending on authorization', () => {
  isEventBasedProcessEnabled.mockReturnValueOnce(true);
  const enabled = shallow(<Header {...props} />);

  expect(enabled.find('[linksTo="/eventBasedProcess/"]')).toExist();

  isEventBasedProcessEnabled.mockReturnValueOnce(false);
  const disabled = shallow(<Header {...props} />);

  expect(disabled.find('[linksTo="/eventBasedProcess/"]')).not.toExist();
});
