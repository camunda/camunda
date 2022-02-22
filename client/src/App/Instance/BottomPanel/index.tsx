/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Pane, PaneHeader, Headline, Pills, PaneBody} from './styled';
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
      <Pane {...this.props}>
        <PaneHeader expandState={this.props.expandState}>
          <Headline>Instance History</Headline>
          <Pills>
            <TimeStampPill />
          </Pills>
        </PaneHeader>
        <PaneBody>
          {this.props.expandState !== 'COLLAPSED' && this.renderChildren()}
        </PaneBody>
      </Pane>
    );
  }
}
