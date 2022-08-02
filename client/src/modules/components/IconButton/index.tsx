/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Button, Icon} from './styled';

interface Props extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  icon: React.ReactNode;
  variant?: 'default' | 'foldable';
  size?: 'medium' | 'large';
  children?: React.ReactNode;
}

const IconButton = React.forwardRef<HTMLButtonElement, Props>(
  function IconButton({children, variant, icon, size, ...props}, ref) {
    return (
      <Button {...props} $variant={variant} ref={ref}>
        <Icon $size={size} $variant={variant}>
          {icon}
        </Icon>
        {children}
      </Button>
    );
  }
);

export {IconButton};
