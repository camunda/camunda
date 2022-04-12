/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import * as Styled from './styled';

type Props = {
  onKeyDown: React.ComponentProps<'li'>['onKeyDown'];
  placement?: 'top' | 'bottom';
  className?: string;
  children?: React.ReactNode;
};

export default class Menu extends React.Component<Props> {
  render() {
    const {onKeyDown, placement, children, className} = this.props;

    return (
      <Styled.Ul
        $placement={placement}
        className={className}
        data-testid="menu"
      >
        {React.Children.map(children, (child, index) => (
          <Styled.Li onKeyDown={onKeyDown} $placement={placement} key={index}>
            {child}
          </Styled.Li>
        ))}
      </Styled.Ul>
    );
  }
}
