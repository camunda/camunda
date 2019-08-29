/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ListItem from './ListItem';

describe('ListItem', () => {
  it('should match snapshot', () => {
    const node = shallow(<ListItem className="list-test">Some child content</ListItem>);

    expect(node).toMatchSnapshot();
  });
});

describe('ListItem.Section', () => {
  it('should match snapshot', () => {
    const node = shallow(
      <ListItem.Section className="list-test">Some child content</ListItem.Section>
    );

    expect(node).toMatchSnapshot();
  });
});
