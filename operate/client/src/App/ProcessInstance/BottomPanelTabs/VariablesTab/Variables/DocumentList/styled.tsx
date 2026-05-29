/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const DocumentList = styled.ul`
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
`;

const DocumentListItem = styled.li`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-03);
  padding: var(--cds-spacing-04) 0;
  border-bottom: 1px solid var(--cds-border-subtle-01);

  &:last-child {
    border-bottom: none;
  }
`;

const DocumentInfo = styled.div`
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-01);
`;

const DocumentFileName = styled.span`
  font-size: var(--cds-body-short-01-font-size);
  font-weight: var(--cds-body-short-01-font-weight);
  line-height: var(--cds-body-short-01-line-height);
  letter-spacing: var(--cds-body-short-01-letter-spacing);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

const DocumentSize = styled.span`
  font-size: var(--cds-caption-01-font-size);
  font-weight: var(--cds-caption-01-font-weight);
  line-height: var(--cds-caption-01-line-height);
  letter-spacing: var(--cds-caption-01-letter-spacing);
  color: var(--cds-text-secondary);
`;

const TruncationNotice = styled.p`
  /* Without !important, the modal sets a padding due to higher specificity,
  which causes the text to be off-center. */
  padding-inline: 0 !important;
  color: var(--cds-text-secondary);
  text-align: center;
`;

export {
  DocumentList,
  DocumentListItem,
  DocumentInfo,
  DocumentFileName,
  DocumentSize,
  TruncationNotice,
};
