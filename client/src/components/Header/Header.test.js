/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

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

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data))
};

jest.mock('./service', () => ({
  getUiConfig: jest.fn().mockReturnValue({
    header: {textColor: 'light', backgroundColor: '#000', logo: 'url'}
  })
}));

it('matches the snapshot', async () => {
  const node = shallow(<Header name="Awesome App" location={{pathname: '/'}} {...props} />);
  await node.update();
  expect(node).toMatchSnapshot();
});
