/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@carbon/react';
import styled from 'styled-components';
import {PreviewDocumentButton} from 'App/ProcessInstance/DocumentsView/DocumentPreview/PreviewDocumentButton';

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

const DocumentAttachments = styled.div`
  margin-block-start: var(--cds-spacing-04);
  inline-size: 100%;
`;

const DocumentList = styled.ul`
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 7rem), 1fr));
  gap: var(--cds-spacing-03);
  list-style: none;
  padding: 0;
  margin-block-start: var(--cds-spacing-03);
`;

const DocumentCard = styled.li`
  min-inline-size: 0;
  max-inline-size: 9rem;
  background: var(--cds-layer-03);
`;

const DocumentPreview = styled(PreviewDocumentButton)`
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--cds-spacing-03);
  padding: var(--cds-spacing-03);
  inline-size: 100%;
  block-size: 6rem;
  max-inline-size: none;
  color: var(--cds-text-primary);
  text-align: start;

  > svg {
    flex-shrink: 0;
  }
`;

const DocumentFileName = styled.span`
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  overflow-wrap: anywhere;
  font-size: var(--cds-label-01-font-size);
  line-height: var(--cds-label-01-line-height);
`;

const DocumentFooter = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-inline-start: var(--cds-spacing-03);
  color: var(--cds-text-secondary);
  font-size: var(--cds-label-01-font-size);
`;

export {
  HiddenButton,
  AttachmentsContainer,
  AttachmentsLabel,
  AttachmentsList,
  Attachment,
  DocumentAttachments,
  DocumentList,
  DocumentCard,
  DocumentPreview,
  DocumentFileName,
  DocumentFooter,
};
