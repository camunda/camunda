/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import AllColumnsButtons from './AllColumnsButtons';
import {Button} from 'components';

it('should invoke enable All when enable all button is clicked', () => {
  const spy = jest.fn();
  const node = shallow(
    <AllColumnsButtons
      allEnabled={false}
      allDisabled={true}
      enableAll={spy}
      disableAll={() => {}}
    />
  );
  node
    .find(Button)
    .at(0)
    .simulate('click');

  expect(spy).toHaveBeenCalled();
});

it('should make the enable all button green when all enabled', () => {
  const node = shallow(<AllColumnsButtons allEnabled={true} allDisabled={false} />);

  expect(
    node
      .find(Button)
      .at(0)
      .props().color
  ).toBe('green');
});

it('should call disableAll when clicking disable all', () => {
  const spy = jest.fn();
  const node = shallow(
    <AllColumnsButtons
      allEnabled={true}
      allDisabled={false}
      enableAll={() => {}}
      disableAll={spy}
    />
  );

  node
    .find(Button)
    .at(1)
    .simulate('click');

  expect(spy).toHaveBeenCalled();
});
