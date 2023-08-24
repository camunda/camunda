/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';
import {
  SkeletonText as BaseSkeletonText,
  SkeletonIcon as BaseSkeletonIcon,
} from '@carbon/react';

const Table = styled.table`
  width: 100%;
  padding-left: var(--cds-spacing-05);
  table-layout: fixed;
  border-collapse: separate;
  border-spacing: var(--cds-spacing-01);
  color: var(--cds-text-secondary);
`;

const Th = styled.th`
  text-align: left;
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
  ${styles.label01};
`;

const Td = styled.td`
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
  ${styles.label02};
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
