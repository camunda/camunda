/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';
import {StructuredListCell as BaseStructuredListCell} from '@carbon/react';

const Container = styled.div`
  overflow-y: auto;
`;

type StructuredListCellProps = {
  $size?: 'sm' | 'md';
  $width?: string;
  $verticalCellPadding?: string;
};

const StructuredListCell = styled(
  BaseStructuredListCell,
)<StructuredListCellProps>`
  ${({$size = 'md', $width, $verticalCellPadding}) => {
    return css`
      vertical-align: top;
      ${$size === 'sm' &&
      css`
        ${styles.label01};
      `}
      ${$width &&
      css`
        width: ${$width};
      `}
      ${$verticalCellPadding &&
      css`
        padding-top: ${$verticalCellPadding} !important;
        padding-bottom: ${$verticalCellPadding} !important;
      `}
    `;
  }}
`;

export {Container, StructuredListCell};
