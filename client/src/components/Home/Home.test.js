/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import HomeWithErrorHandling from './Home';
import {loadEntities} from './service';

const Home = HomeWithErrorHandling.WrappedComponent;

jest.mock('./service', () => ({
  loadEntities: jest.fn().mockReturnValue({isEntitiesList: true})
}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data))
};

it('should load entities', () => {
  shallow(<Home {...props} />);

  expect(loadEntities).toHaveBeenCalled();
});

it('should pass loaded entities to the EntityList', () => {
  const node = shallow(<Home {...props} />);

  expect(node).toMatchSnapshot();
});
