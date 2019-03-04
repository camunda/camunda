/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import MetricPanel from './MetricPanel';
import * as Styled from './styled.js';

const Panel = (
  <MetricPanel>
    <span />
    <span />
    <span />
  </MetricPanel>
);

describe('MetricPanel', () => {
  it('should render a list', () => {
    const node = shallow(Panel);
    node.find(Styled.Ul);
  });

  it('should render all props as children of list', () => {
    const node = shallow(Panel);
    expect(node.find(Styled.Ul).children().length).toBe(3);
  });
});
