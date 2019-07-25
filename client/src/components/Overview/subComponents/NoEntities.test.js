/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {Link} from 'react-router-dom';

import {Button} from 'components';

import NoEntities from './NoEntities';

it('should have a button to create a new entity', () => {
  const spy = jest.fn();
  const node = shallow(<NoEntities label="Report" createFunction={spy} />);

  expect(node.find(Button)).toExist();
  node.find(Button).simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should have a link to go to a creation page', () => {
  const node = shallow(<NoEntities label="Report" link="/createEntity" />);

  expect(node.find(Link)).toExist();
  expect(node.find(Link).prop('to')).toBe('/createEntity');
});
