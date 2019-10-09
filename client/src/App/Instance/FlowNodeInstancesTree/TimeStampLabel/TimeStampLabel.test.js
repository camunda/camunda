/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {FlowNodeTimeStampProvider} from 'modules/contexts/FlowNodeTimeStampContext';

import TimeStampLabel from './TimeStampLabel';

import * as Styled from './styled';

const renderNode = node => {
  return mount(
    <ThemeProvider>
      <FlowNodeTimeStampProvider>{node}</FlowNodeTimeStampProvider>
    </ThemeProvider>
  );
};

describe('TimeStampLabel', () => {
  let node;

  beforeEach(() => {
    node = renderNode(
      <TimeStampLabel isSelected={false} timestamp={'01 Dec 2018 00:00:00'} />
    );
  });

  it('should not render a time stamp as a default', () => {
    expect(node.find(Styled.TimeStamp)).not.toExist();
    expect(node.find(TimeStampLabel).text()).toBe('');
  });

  it('should not render a component if no timestamp is passed', () => {
    node = renderNode(<TimeStampLabel isSelected={false} />);
    expect(node.find(Styled.TimeStamp)).not.toExist();
  });
});
