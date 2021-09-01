/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import * as Styled from './styled';

interface Props extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  iconButtonTheme: 'default' | 'foldable';
  size?: 'medium' | 'large';
  icon?: React.ReactNode;
}

const IconButton = React.forwardRef<any, Props>(function IconButton(
  {children, iconButtonTheme, icon, size, ...props},
  ref
) {
  return (
    <Styled.Button {...props} iconButtonTheme={iconButtonTheme} ref={ref}>
      <Styled.Icon size={size} iconButtonTheme={iconButtonTheme}>
        {icon}
      </Styled.Icon>
      {children}
    </Styled.Button>
  );
});

export default IconButton;
