/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {Container, CheckIcon, WarningIcon} from './styled';

type Props = {
  children: React.ReactNode;
  className?: string;
  variant: 'default' | 'error' | 'success';
};

const StatusMessage = styled<React.FC<Props>>(
  ({children, variant, className}) => {
    return (
      <Container $variant={variant} className={className}>
        {variant === 'success' && <CheckIcon />}
        {variant === 'error' && <WarningIcon />}
        {children}
      </Container>
    );
  }
)``;

export {StatusMessage};
