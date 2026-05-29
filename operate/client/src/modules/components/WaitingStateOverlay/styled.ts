/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {Stack} from '@carbon/react';

const waitStateBadge = {
  background: '#fcf5d',
  color: '#dbb340',
};

const Container = styled(Stack)<{$theme: 'dark' | 'light'}>`
  align-items: center;
  font-size: var(--cds-label-01-font-size);
  font-weight: var(--cds-label-01-font-weight);
  line-height: var(--cds-label-01-line-height);
  letter-spacing: var(--cds-label-01-letter-spacing);
  border-radius: 11px;
  border: 1px solid ${waitStateBadge.color};
  background-color: color-mix(
    in srgb,
    ${waitStateBadge.background} 20%,
    transparent
  );
  color: ${waitStateBadge.color};
  padding: var(--cds-spacing-02) var(--cds-spacing-04);
  white-space: nowrap;
`;

export {Container};
