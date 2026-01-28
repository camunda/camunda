/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {
  spacing05,
  spacing06,
  borderSubtle01,
  iconSecondary,
  iconPrimary,
  supportInfo,
  textSecondary,
  background,
} from '@carbon/elements';

const Container = styled.div`
  display: flex;
  flex-direction: column;
`;

const SectionTitle = styled.h4`
  margin-bottom: 20px;
`;

const TimelineList = styled.div`
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0;
  /* outer padding only */
  padding: 0 0;
`;

const TimelineRow = styled.div<{$isFirst?: boolean; $isLast?: boolean}>`
  position: relative;
  display: grid;
  grid-template-columns: 1.25rem 1fr;
  column-gap: ${spacing05};
  /* no extra spacing between rows */
  padding: 0;

  ${({$isFirst}) =>
    $isFirst &&
    css`
      ${TimelineRail}::before {
        top: 1.1rem;
      }
    `}

  ${({$isLast}) =>
    $isLast &&
    css`
      ${TimelineRail}::before {
        bottom: calc(100% - 1.1rem);
      }
    `}
`;

const TimelineRail = styled.div`
  position: relative;
  width: 1.25rem;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    bottom: 0;
    left: 50%;
    transform: translateX(-50%);
    width: 2px;
    background: ${borderSubtle01};
  }
`;

const TimelineDot = styled.div<{
  $variant?: 'default' | 'header' | 'status';
}>`
  position: absolute;
  left: 50%;
  top: 1.1rem;
  transform: translate(-50%, -50%);
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: ${iconSecondary};
  border: 2px solid ${background};

  ${({$variant}) =>
    $variant === 'header' &&
    css`
      background: ${iconPrimary};
    `}

  ${({$variant}) =>
    $variant === 'status' &&
    css`
      background: ${supportInfo};
    `}
`;

const RowContent = styled.div`
  min-width: 0;
  padding: 0;
`;

const ItemHeader = styled.div`
  display: flex;
  align-items: center;
  min-height: 2.5rem;
`;

const ItemMeta = styled.div`
  display: flex;
  gap: ${spacing05};
  align-items: center;
  color: ${textSecondary};
`;

const ItemBody = styled.div`
  padding-left: calc(1.25rem + ${spacing05});
  display: flex;
  flex-direction: column;
  gap: ${spacing05};
`;

export {
  Container,
  SectionTitle,
  TimelineList,
  TimelineRow,
  TimelineRail,
  TimelineDot,
  RowContent,
  ItemHeader,
  ItemMeta,
  ItemBody,
};
