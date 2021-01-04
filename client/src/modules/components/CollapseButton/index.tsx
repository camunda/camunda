/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {DIRECTION} from 'modules/constants';
import * as Styled from './styled';

const iconsMap = {
  [DIRECTION.UP]: Styled.Up,
  [DIRECTION.DOWN]: Styled.Down,
  [DIRECTION.LEFT]: Styled.Left,
  [DIRECTION.RIGHT]: Styled.Right,
};

interface Props extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  direction: 'UP' | 'DOWN' | 'RIGHT' | 'LEFT';
  onClick?: (...args: any[]) => any;
}

const CollapseButton = React.forwardRef<any, Props>(function CollapseButton(
  {direction, onClick, ...props},
  ref
) {
  const TargetIcon = iconsMap[direction];

  return (
    <Styled.CollapseButton ref={ref} {...props} onClick={onClick}>
      <TargetIcon data-testid={`icon-${direction}`} />
    </Styled.CollapseButton>
  );
});

export default CollapseButton;
