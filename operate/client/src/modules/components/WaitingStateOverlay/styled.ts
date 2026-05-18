/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {Stack} from '@carbon/react';

const Container = styled(Stack)<{$theme: 'dark' | 'light'}>`
  ${({$theme}) => {
    const backgroundColor =
      $theme === 'light'
        ? 'var(--cds-background-inverse)'
        : 'var(--cds-layer-02)';
    const textColor =
      $theme === 'light'
        ? 'var(--cds-text-inverse)'
        : 'var(--cds-text-primary)';

    return css`
      align-items: center;
      font-weight: 400;
      font-size: 12px;
      height: 22px;
      border-radius: 11px;
      background-color: ${backgroundColor};
      color: ${textColor};
      padding: var(--cds-spacing-02) var(--cds-spacing-04);
      white-space: nowrap;
      transform: translateX(-50%);
    `;
  }}
`;

export {Container};
