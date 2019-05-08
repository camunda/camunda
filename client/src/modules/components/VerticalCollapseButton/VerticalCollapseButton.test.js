/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import VerticalCollapseButton from './VerticalCollapseButton';

describe('VerticalCollapseButton', () => {
  it('should render a button', () => {
    const label = 'Random label';
    const children = <button>Child content</button>;
    const node = shallow(
      <VerticalCollapseButton label={label}>{children}</VerticalCollapseButton>
    );

    expect(node.props().title).toBe(`Expand ${label}`);

    expect(node).toMatchSnapshot();
  });
});
