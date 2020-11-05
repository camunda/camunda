/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import * as Styled from './styled';

type Props = {
  size: 'small' | 'medium' | 'large';
  color?: 'main' | 'primary' | 'secondary';
};

const Button = React.forwardRef<HTMLButtonElement, Props>(function Button(
  props,
  ref
) {
  return <Styled.Button {...props} ref={ref} />;
});

Button.defaultProps = {
  color: 'main',
};

export default Button;
