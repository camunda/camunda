/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import Copyright from 'modules/components/Copyright';
import {FlowNodeTimeStampProvider} from 'modules/contexts/FlowNodeTimeStampContext';
import {EXPAND_STATE} from 'modules/constants';

import * as Styled from './styled';

import TimeStampPill from './TimeStampPill';

export default class BottomPanel extends React.PureComponent {
  static propTypes = {
    expandState: PropTypes.oneOf(Object.values(EXPAND_STATE)),
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node
    ])
  };

  renderChildren() {
    return React.Children.map(
      this.props.children,
      child =>
        child &&
        React.cloneElement(child, {
          expandState: this.props.expandState
        })
    );
  }

  render() {
    return (
      <Styled.Pane {...this.props}>
        <FlowNodeTimeStampProvider>
          <Styled.PaneHeader>
            <Styled.Headline>Instance History</Styled.Headline>
            <Styled.Pills>
              <TimeStampPill />
            </Styled.Pills>
          </Styled.PaneHeader>
          <Styled.PaneBody>{this.renderChildren()}</Styled.PaneBody>
          <Styled.PaneFooter>
            <Copyright />
          </Styled.PaneFooter>
        </FlowNodeTimeStampProvider>
      </Styled.Pane>
    );
  }
}
