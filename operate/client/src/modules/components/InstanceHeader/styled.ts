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

const Table = styled.table`
  table-layout: fixed;
  border-collapse: separate;
  border-spacing: 0 var(--cds-spacing-01);
  color: var(--cds-text-secondary);

  th:not(:first-child),
  td:not(:first-child) {
    padding-left: var(--cds-spacing-07);
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
      min-width: 0;
      align-items: center;
      gap: var(--cds-spacing-05);
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

const AdditionalContent = styled.div`
  margin-left: auto;
  flex-shrink: 0;
`;

const NameContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-01);
  min-width: 0;
  flex-shrink: 1;
  margin-right: var(--cds-spacing-05);
`;

const InstanceName = styled.span`
  ${styles.label02};
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
  color: var(--cds-text-secondary);

  &:has(+ span) {
    ${styles.label01};
  }
`;

const IncidentCount = styled.span`
  ${styles.label02};
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
  color: var(--cds-support-error);
`;

const SkeletonText = styled(BaseSkeletonText)`
  margin: 0;
`;

const SkeletonIcon = styled(BaseSkeletonIcon)`
  width: var(--cds-spacing-06);
  height: var(--cds-spacing-06);
`;

export {
  Table,
  Td,
  Th,
  Container,
  AdditionalContent,
  SkeletonText,
  SkeletonIcon,
  NameContainer,
  InstanceName,
  IncidentCount,
};
