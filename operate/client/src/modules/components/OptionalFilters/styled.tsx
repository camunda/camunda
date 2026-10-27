/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
  width: unset;
  padding: 0 var(--cds-spacing-04);

  svg {
    fill: var(--cds-link-primary) !important; // Carbon styles are wrong
  }
`;

const ButtonStack = styled(Stack)`
  align-items: center;
`;

export {Container, OverflowMenu, ButtonStack};
