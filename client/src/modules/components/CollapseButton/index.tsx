/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {
  CollapseButton as StyledCollapseButton,
  Up,
  Down,
  Left,
  Right,
} from './styled';

interface Props extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  direction: 'UP' | 'DOWN' | 'RIGHT' | 'LEFT';
  onClick?: (...args: any[]) => any;
}

const CollapseButton = React.forwardRef<any, Props>(function CollapseButton(
  {direction, onClick, ...props},
  ref
) {
  return (
    <StyledCollapseButton ref={ref} {...props} onClick={onClick}>
      {direction === 'UP' && <Up data-testid="icon-up" />}
      {direction === 'DOWN' && <Down data-testid="icon-down" />}
      {direction === 'LEFT' && <Left data-testid="icon-left" />}
      {direction === 'RIGHT' && <Right data-testid="icon-right" />}
    </StyledCollapseButton>
  );
});

export default CollapseButton;
