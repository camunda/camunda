/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import ContextualMessage from './ContextualMessage';
import {MESSAGES} from './constants';
import * as Styled from './styled';

describe('ContextualMessage', () => {
  let node;

  beforeEach(() => {
    node = shallow(<ContextualMessage type={MESSAGES.DROP_SELECTION} />);
  });

  it('should render a circual component', () => {
    expect(node.find(Styled.Dot)).toExist();
  });

  it('should render a message according to its type', () => {
    expect(node.find(Styled.Text)).toExist();
    expect(node.find(Styled.Text).contains(MESSAGES.DROP_SELECTION));
  });
});
