/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {OverflowMenu as BaseOverflowMenu, Stack} from '@carbon/react';

const Container = styled.div`
  display: flex;
  justify-content: end;

  .cds--popover[role='tooltip'] {
    display: none;
  }
`;

const OverflowMenu = styled(BaseOverflowMenu)`
  color: var(--cds-link-primary);
  &:hover {
    color: var(--cds-link-primary);
  }
  width: unset;
`;

const ButtonStack = styled(Stack)`
  align-items: center;
`;

export {Container, OverflowMenu, ButtonStack};
