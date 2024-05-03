/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {COLLAPSABLE_PANEL_HEADER_HEIGHT} from 'modules/constants';

type HeaderProps = {
  $size?: 'sm' | 'md';
};

const Header = styled.header<HeaderProps>`
  ${({$size = 'md'}) => {
    return css`
      background-color: var(--cds-layer);
      border-bottom: solid 1px var(--cds-border-subtle-01);
      padding: var(--cds-spacing-04) var(--cds-spacing-05);
      display: flex;
      align-items: center;

      ${$size === 'md' &&
      css`
        min-height: ${COLLAPSABLE_PANEL_HEADER_HEIGHT};
        height: ${COLLAPSABLE_PANEL_HEADER_HEIGHT};
      `}
      ${$size === 'sm' &&
      css`
        min-height: var(--cds-spacing-08);
        height: var(--cds-spacing-08);
      `}
    `;
  }}
`;

export {Header};
