/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import Copyright from 'modules/components/Copyright';

import * as Styled from './styled';

import {TimeStampPill} from './TimeStampPill';

type Props = {
  expandState?: 'DEFAULT' | 'EXPANDED' | 'COLLAPSED';
};

export default class BottomPanel extends React.PureComponent<Props> {
  renderChildren() {
    return React.Children.map(
      this.props.children,
      (child) =>
        child &&
        // @ts-expect-error ts-migrate(2769) FIXME: Type 'string' is not assignable to type 'ReactElem... Remove this comment to see the full error message
        React.cloneElement(child, {
          expandState: this.props.expandState,
        })
    );
  }

  render() {
    return (
      <Styled.Pane {...this.props}>
        <Styled.PaneHeader>
          <Styled.Headline>Instance History</Styled.Headline>
          <Styled.Pills>
            <TimeStampPill />
          </Styled.Pills>
        </Styled.PaneHeader>
        <Styled.PaneBody>
          {this.props.expandState !== 'COLLAPSED' && this.renderChildren()}
        </Styled.PaneBody>
        <Styled.PaneFooter>
          <Copyright />
        </Styled.PaneFooter>
      </Styled.Pane>
    );
  }
}
