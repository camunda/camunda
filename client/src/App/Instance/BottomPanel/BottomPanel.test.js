/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {HashRouter as Router} from 'react-router-dom';
import {ThemeProvider} from 'modules/contexts/ThemeContext';

import Copyright from 'modules/components/Copyright';

import BottomPanel from './BottomPanel';
import TimeStampPill from './TimeStampPill';

import * as Styled from './styled';

jest.mock('./TimeStampPill', () => {
  return function TimeStampPill() {
    return <div />;
  };
});

describe('BottomPanel', () => {
  let node;
  let ChildNode;
  beforeEach(() => {
    // eslint-disable-next-line react/prop-types
    ChildNode = ({expandState, ...props}) => (
      <div {...props} data-test="ChildNode" />
    );
    node = mount(
      <Router>
        <ThemeProvider>
          <BottomPanel>
            <ChildNode />
          </BottomPanel>
        </ThemeProvider>
      </Router>
    );
  });

  it('should render a header', () => {
    //Pane Header
    const PaneHeaderNode = node.find(Styled.PaneHeader);
    const Headline = node.find(Styled.Headline);

    expect(PaneHeaderNode).toHaveLength(1);
    expect(Headline).toHaveLength(1);
    expect(Headline.text()).toEqual('Instance History');
    expect(node.find(TimeStampPill)).toHaveLength(1);
  });

  it('should render children', () => {
    const PaneBodyNode = node.find(Styled.PaneBody);
    const child = PaneBodyNode.find(ChildNode);
    expect(child).toExist();
  });

  it('should render a footer', () => {
    // Pane Footer
    const PaneFooterNode = node.find(Styled.PaneFooter);
    expect(PaneFooterNode).toHaveLength(1);

    // Copyright
    const CopyrightNode = PaneFooterNode.find(Copyright);
    expect(CopyrightNode).toHaveLength(1);
  });
});
