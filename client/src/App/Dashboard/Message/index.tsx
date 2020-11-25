/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import styled from 'styled-components';
import {Container, CheckIcon, WarningIcon} from './styled';

type Props = {
  variant: 'default' | 'success' | 'error';
  className?: string;
  children: React.ReactNode;
};

const Message = styled<React.FC<Props>>(({children, variant, className}) => {
  return (
    <Container $variant={variant} className={className}>
      {variant === 'success' && <CheckIcon />}
      {variant === 'error' && <WarningIcon />}
      {children}
    </Container>
  );
})``;

export {Message};
