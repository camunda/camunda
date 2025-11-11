/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {Stack} from '@carbon/react';
import {styles} from '@carbon/elements';
import {StructuredListCell} from '@carbon/react';
import {StructuredListRow} from '@carbon/react';

const TimelineContainer = styled.div`
  height: 100%;
  overflow: auto;
  padding: var(--cds-spacing-05);
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-03);
`;

const TimelineItem = styled.div`
  display: grid;
  grid-template-columns: auto 1fr;
  grid-template-rows: auto 1fr;
  gap: 0 var(--cds-spacing-05);
  position: relative;
`;

type MarkerProps = {
  $state: string;
};

const TimelineMarker = styled.div<MarkerProps>`
  ${({$state}) => {
    let backgroundColor = 'var(--cds-icon-secondary)';

    switch ($state) {
      case 'COMPLETED':
        backgroundColor = 'var(--cds-support-success)';
        break;
      case 'FAILED':
        backgroundColor = 'var(--cds-support-error)';
        break;
      default:
        backgroundColor = 'var(--cds-icon-secondary)';
        break;
    }

    return css`
      width: 12px;
      height: 12px;
      border-radius: 50%;
      background-color: ${backgroundColor};
      margin-top: var(--cds-spacing-03);
      grid-column: 1;
      grid-row: 1;
      z-index: 1;
      flex-shrink: 0;
    `;
  }}
`;

const TimelineLine = styled.div`
  width: 2px;
  background-color: var(--cds-border-subtle-01);
  grid-column: 1;
  grid-row: 2;
  margin-left: 5px;
  height: 100%;
  min-height: var(--cds-spacing-06);
`;

const TimelineContent = styled.div`
  grid-column: 2;
  grid-row: 1 / span 2;
  padding-bottom: var(--cds-spacing-05);
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-03);
`;

const TimelineHeader = styled.div`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-03);
  flex-wrap: wrap;
`;

const TimelineTitle = styled.h3`
  ${styles.headingCompact01};
  color: var(--cds-text-primary);
  margin: 0;
`;

const TimelineMetadata = styled.div`
  ${styles.label01};
  color: var(--cds-text-secondary);
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-03);
`;

const TimelineBody = styled.div`
  ${styles.bodyShort01};
  color: var(--cds-text-primary);
  padding: var(--cds-spacing-03);
  background-color: var(--cds-layer-01);
  border-radius: 4px;
  border-left: 3px solid var(--cds-border-subtle-02);
`;

const TimelineActions = styled(Stack)`
  ${styles.label01};
`;

const Title = styled.h4`
  margin-top: var(--cds-spacing-04);
  margin-bottom: var(--cds-spacing-03);
`;

const Subtitle = styled.h6`
  margin-bottom: var(--cds-spacing-02);
`;

const Key = styled.div`
  font-weight: 600;
`;

const Value = styled.div`
  white-space: pre-wrap;
  word-break: break-all;
`;

const Property = styled.div`
  display: flex;
  gap: var(--cds-spacing-02);
`;

const FirstColumn = styled(StructuredListCell)`
  width: 176px;
`;

const VerticallyAlignedRow = styled(StructuredListRow)`
  &.cds--structured-list-row {
    & > .cds--structured-list-td {
      vertical-align: middle;
    }
  }
`;

export {
  TimelineContainer,
  TimelineItem,
  TimelineMarker,
  TimelineContent,
  TimelineHeader,
  TimelineTitle,
  TimelineMetadata,
  TimelineBody,
  TimelineActions,
  TimelineLine,
  Title,
  Subtitle,
  Key,
  Value,
  Property,
  FirstColumn,
  VerticallyAlignedRow,
};
