/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';

type Size = 'small' | 'medium' | 'large';

type WrapperProps = {
  $size: Size;
};

const getFontStyle = ({$size}: WrapperProps) => {
  return css`
    ${$size === 'small' &&
    css`
      ${styles.bodyCompact01};
      color: var(--cds-text-secondary);
    `}
    ${$size === 'medium' &&
    css`
      ${styles.heading01};
      color: var(--cds-text-primary);
    `}
    ${$size === 'large' &&
    css`
      ${styles.heading02};
      color: var(--cds-text-primary);
    `}
  `;
};

const Wrapper = styled.div<WrapperProps>`
  ${({$size}) => {
    return css`
      display: flex;
      ${getFontStyle({$size})};
    `;
  }}
`;

type IncidentsCountProps = {
  $hasIncidents?: boolean;
};

const IncidentsCount = styled.div<IncidentsCountProps>`
  ${({$hasIncidents}) => {
    return css`
      min-width: var(--cds-spacing-09);
      ${$hasIncidents
        ? css`
            color: var(--cds-text-error);
          `
        : css`
            color: var(--cds-text-secondary);
          `}
    `;
  }}
`;

type ActiveCountProps = {
  $hasActiveInstances?: boolean;
};

const ActiveCount = styled.div<ActiveCountProps>`
  ${({$hasActiveInstances}) => {
    return css`
      margin-left: auto;
      width: 139px;
      text-align: right;

      color: ${$hasActiveInstances
        ? 'var(--cds-tag-color-green)'
        : 'var(--cds-text-primary)'};
    `;
  }}
`;

type LabelProps = {
  $isRed?: boolean;
  $size?: 'small' | 'medium';
};

const Label = styled.div<LabelProps>`
  ${({$size, $isRed}) => {
    return css`
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      ${styles.bodyCompact01}
      color: var(--cds-text-secondary);
      ${$size === 'medium' &&
      css`
        ${styles.headingCompact01}
        color: var(--cds-text-primary);
      `}
      ${$isRed &&
      css`
        color: var(--cds-text-error);
      `}
    `;
  }}
`;

const BarContainer = styled.div`
  position: relative;
  margin: var(--cds-spacing-03) 0;
`;

type ActiveBarProps = {
  $isPassive?: boolean;
  $size: Size;
};

const getBarStyles = ($size: Size) => {
  return css`
    ${$size === 'small' &&
    css`
      height: var(--cds-spacing-01);
    `}
    ${$size === 'medium' &&
    css`
      height: var(--cds-spacing-02);
    `}
    ${$size === 'large' &&
    css`
      height: var(--cds-spacing-03);
    `}
  `;
};

const ActiveInstancesBar = styled.div<ActiveBarProps>`
  ${({$isPassive, $size}) => {
    return css`
      ${getBarStyles($size)};
      background: ${$isPassive
        ? 'var(--cds-border-subtle-01)'
        : 'var(--cds-support-success)'};
    `;
  }}
`;

type IncidentsBarProps = {
  $size: Size;
};

const IncidentsBar = styled.div<IncidentsBarProps>`
  ${({$size}) => {
    return css`
      ${getBarStyles($size)};
      position: absolute;
      top: 0;
      background: var(--cds-support-error);
    `;
  }}
`;

export {
  Wrapper,
  IncidentsCount,
  ActiveCount,
  Label,
  BarContainer,
  ActiveInstancesBar,
  IncidentsBar,
};
