/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import ControlGroup from './ControlGroup';

it('should render without crashing', () => {
  mount(<ControlGroup />);
});

it('should render its children', () => {
  const node = mount(
    <ControlGroup>
      <div className="foo" />
    </ControlGroup>
  );

  expect(node.find('.foo')).toExist();
});

it('should render additonal classNames', () => {
  const node = mount(<ControlGroup className="bar" />);

  expect(node.find('.ControlGroup.bar')).toExist();
});

it('should render a modifier class reflecting a layout property', () => {
  const node = mount(<ControlGroup layout="horizontal" />);

  expect(node.find('.ControlGroup.ControlGroup--horizontal')).toExist();
});
