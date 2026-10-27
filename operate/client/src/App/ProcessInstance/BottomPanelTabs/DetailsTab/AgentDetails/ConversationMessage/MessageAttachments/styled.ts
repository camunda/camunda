/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@carbon/react';
import styled from 'styled-components';

const HiddenButton = styled(Button)`
  opacity: 0;

  &:focus-visible {
    opacity: 1;
  }
`;

const AttachmentsContainer = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--cds-spacing-02);
  inline-size: fit-content;
  margin-block-start: var(--cds-spacing-04);

  &:hover ${HiddenButton} {
    opacity: 1;
  }
`;

const AttachmentsLabel = styled.h6`
  font-size: var(--cds-label-01-font-size);
  font-weight: var(--cds-label-01-font-weight);
  line-height: var(--cds-label-01-line-height);
  letter-spacing: var(--cds-label-01-letter-spacing);
  color: var(--cds-text-secondary);
`;

const AttachmentsList = styled.ul`
  display: contents;
  list-style: none;
  padding-inline-start: 0;
`;

// NOTE: These cannot reuse slightly different looking "Tag" components from Carbon,
// because they are bigger and hide their icons on the smallest size.
const Attachment = styled.li`
  display: inline-flex;
  align-items: center;
  gap: var(--cds-spacing-02);
  padding: var(--cds-spacing-01) var(--cds-spacing-03);
  border-radius: 100px; // pill shape
  font-size: var(--cds-label-01-font-size);
  font-weight: var(--cds-label-01-font-weight);
  line-height: var(--cds-label-01-line-height);
  letter-spacing: var(--cds-label-01-letter-spacing);
  color: var(--cds-text-primary);
  border: 1px solid var(--cds-border-subtle-01);
  white-space: nowrap;

  & > svg {
    color: var(--cds-icon-secondary);
  }
`;

export {
  HiddenButton,
  AttachmentsContainer,
  AttachmentsLabel,
  AttachmentsList,
  Attachment,
};
