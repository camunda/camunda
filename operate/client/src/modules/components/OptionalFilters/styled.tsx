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
  align-items: center;
  gap: var(--cds-spacing-04);

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
  padding: 0 var(--cds-spacing-04);
`;

const ButtonStack = styled(Stack)`
  align-items: center;
`;

const TriggerButton = styled.button`
  color: var(--cds-link-primary);
  &:hover {
    color: var(--cds-link-primary);
  }
  width: unset;
  padding: 0 var(--cds-spacing-04);
  background: transparent;
  border: none;
  cursor: pointer;
`;

export {Container, OverflowMenu, ButtonStack, TriggerButton};
