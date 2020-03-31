/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {HashRouter as Router} from 'react-router-dom';
import {ThemeProvider} from 'modules/contexts/ThemeContext';

import Pill from './Pill';
import {PILL_TYPE} from 'modules/constants';

import * as Styled from './styled';

const mountNode = (node) => {
  const mountedNode = mount(
    <Router>
      <ThemeProvider>{node}</ThemeProvider>
    </Router>
  );
  return mountedNode.find(Pill);
};

describe('Pill', () => {
  let node;
  const labelString = 'Some Label';

  it('should render children', () => {
    // given
    node = mountNode(<Pill isActive={true}>{labelString}</Pill>);

    expect(node.text()).toEqual(labelString);
  });

  it('should render without icon if no type is passed', () => {
    // given
    node = mountNode(<Pill isActive={true}>{labelString}</Pill>);
    expect(node.find(Styled.Clock)).not.toExist();
  });

  it('should render without icon if a unknown type is passed', () => {
    // silence prop-type warning
    console.error = jest.fn();

    node = mountNode(
      <Pill isActive={true} type={'someUnknownType'}>
        {labelString}
      </Pill>
    );
    expect(node).toExist();
  });

  describe('icon type', () => {
    it('should render clock icon', () => {
      // given
      node = mountNode(
        <Pill isActive={true} type={PILL_TYPE.TIMESTAMP}>
          {labelString}
        </Pill>
      );

      expect(node.find(Styled.Clock)).toExist();
    });
  });
});
