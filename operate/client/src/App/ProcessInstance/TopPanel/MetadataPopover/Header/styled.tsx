/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {styles} from '@carbon/elements';
import {Stack as BaseStack} from '@carbon/react';
import styled, {css} from 'styled-components';

type TitleProps = {
  $variant?: 'default' | 'error';
};

const Title = styled.h2<TitleProps>`
  ${({$variant = 'default'}) => css`
    ${styles.headingCompact01};
    ${$variant === 'error'
      ? css`
          color: var(--cds-support-error);
        `
      : null}
  `};
`;

const Header = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--cds-spacing-07);
`;

const Stack = styled(BaseStack)`
  align-items: center;
`;

export {Title, Header, Stack};
