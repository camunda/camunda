/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
