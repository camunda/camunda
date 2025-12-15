/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';
import {
  SkeletonText as BaseSkeletonText,
  SkeletonIcon as BaseSkeletonIcon,
} from '@carbon/react';
import {BREAKPOINTS} from 'modules/constants';

const Table = styled.table`
  width: 100%;
  padding-left: var(--cds-spacing-05);
  table-layout: fixed;
  border-collapse: separate;
  border-spacing: var(--cds-spacing-01);
  color: var(--cds-text-secondary);

  @media (max-width: ${BREAKPOINTS.lg - 1}px) {
    th:nth-child(n + 3),
    td:nth-child(n + 3) {
      display: none;
    }
  }
`;

const Th = styled.th`
  text-align: left;
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
  ${styles.label01};
`;

type TDProps = {
  $hideOverflowingContent?: boolean;
};

const Td = styled.td<TDProps>`
  ${({$hideOverflowingContent = true}) => {
    return css`
      ${styles.label02};
      ${$hideOverflowingContent &&
      css`
        text-overflow: ellipsis;
        overflow: hidden;
        white-space: nowrap;
      `}
    `;
  }}
`;

type ContainerProps = {
  $hideBottomBorder?: boolean;
};

const Container = styled.header<ContainerProps>`
  ${({$hideBottomBorder}) => {
    return css`
      display: flex;
      align-items: center;
      background-color: var(--cds-layer-01);
      padding: var(--cds-spacing-02) var(--cds-spacing-05);
      border-bottom: 1px solid var(--cds-border-subtle-01);
      ${$hideBottomBorder &&
      css`
        border-bottom: none;
      `}
    `;
  }}
`;

const SkeletonText = styled(BaseSkeletonText)`
  margin: 0;
`;

const SkeletonIcon = styled(BaseSkeletonIcon)`
  width: var(--cds-spacing-06);
  height: var(--cds-spacing-06);
`;

export {Table, Td, Th, Container, SkeletonText, SkeletonIcon};
