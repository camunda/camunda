/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import * as Styled from './styled';

type Props = {
  onKeyDown: (...args: any[]) => any;
  placement?: 'top' | 'bottom';
  transitionTiming?: any;
  className?: string;
};

export default class Menu extends React.Component<Props> {
  render() {
    const {onKeyDown, placement, children, transitionTiming, className} =
      this.props;

    return (
      <Styled.Ul
        // @ts-expect-error ts-migrate(2769) FIXME: Property 'placement' does not exist on type 'Intri... Remove this comment to see the full error message
        placement={placement}
        className={className}
        transitionTiming={transitionTiming}
        data-testid="menu"
      >
        {React.Children.map(children, (child, index) => (
          // @ts-expect-error ts-migrate(2769) FIXME: Property 'placement' does not exist on type 'Intri... Remove this comment to see the full error message
          <Styled.Li onKeyDown={onKeyDown} placement={placement} key={index}>
            {child}
          </Styled.Li>
        ))}
      </Styled.Ul>
    );
  }
}
