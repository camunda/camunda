/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import VerticalExpandButton from './VerticalExpandButton';

describe('VerticalExpandButton', () => {
  it('should render a button', () => {
    const label = 'Random label';
    const children = <button>Child content</button>;
    const node = shallow(
      <VerticalExpandButton label={label}>{children}</VerticalExpandButton>
    );

    expect(node.props().title).toBe(`Expand ${label}`);

    expect(node).toMatchSnapshot();
  });
});
