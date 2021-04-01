/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import * as Styled from './styled';

type Props = {
  iconButtonTheme: 'default' | 'incidentsBanner' | 'foldable';
  disabled?: boolean;
  title?: string;
  onClick?: () => void;
  size?: 'medium' | 'large';
  children?: React.ReactNode;
  icon?: React.ReactNode;
};

const IconButton = React.forwardRef<any, Props>(function IconButton(
  {children, iconButtonTheme, icon, size, ...props},
  ref
) {
  return (
    <Styled.Button {...props} iconButtonTheme={iconButtonTheme} ref={ref}>
      {/* @ts-expect-error ts-migrate(2769) FIXME: Property 'size' does not exist on type 'IntrinsicA... Remove this comment to see the full error message */}
      <Styled.Icon size={size} iconButtonTheme={iconButtonTheme}>
        {icon}
      </Styled.Icon>
      {children}
    </Styled.Button>
  );
});

export default IconButton;
