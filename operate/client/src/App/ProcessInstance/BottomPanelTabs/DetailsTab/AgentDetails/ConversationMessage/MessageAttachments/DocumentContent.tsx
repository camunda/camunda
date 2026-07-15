/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {memo, useId} from 'react';
import type {AgentInstanceHistoryItem} from '@camunda/camunda-api-zod-schemas/8.10';
import {Document, View} from '@carbon/react/icons';
import {
  AttachmentsContainer,
  AttachmentsLabel,
  Attachment,
  HiddenButton,
  AttachmentsList,
} from './styled';
import {ModalStateManager} from 'modules/components/ModalStateManager';
import {toDocumentInfo} from 'App/ProcessInstance/DocumentsView/documentInfo';
import {DocumentListModal} from 'App/ProcessInstance/DocumentsView/DocumentListModal';
import {middleTruncate} from 'App/ProcessInstance/DocumentsView/middleTruncate';

const MAX_VISIBLE_DOCUMENTS = 3;

type ContentItem = AgentInstanceHistoryItem['content'][number];

type Props = {
  content: ContentItem[];
  modalTitleSuffix: string;
};

const DocumentContent: React.FC<Props> = memo(function DocumentContent({
  content,
  modalTitleSuffix,
}) {
  const id = useId();

  const documents = content
    .filter((entry) => entry.contentType === 'DOCUMENT')
    .map(({documentReference}) => toDocumentInfo(documentReference));

  if (documents.length === 0) {
    return null;
  }

  const visibleDocuments = documents.slice(0, MAX_VISIBLE_DOCUMENTS);
  const hiddenDocumentsCount = documents.length - visibleDocuments.length;

  return (
    <AttachmentsContainer>
      <AttachmentsLabel id={id}>Documents</AttachmentsLabel>
      <AttachmentsList aria-labelledby={id}>
        {visibleDocuments.map((document, i) => (
          <Attachment key={i} title={document.fileName}>
            <Document size={12} />
            {middleTruncate(document.fileName, 20)}
          </Attachment>
        ))}
        {hiddenDocumentsCount > 0 && (
          <Attachment>{hiddenDocumentsCount} more</Attachment>
        )}
      </AttachmentsList>
      <ModalStateManager
        renderLauncher={({setOpen}) => (
          <HiddenButton
            kind="ghost"
            size="xs"
            hasIconOnly
            renderIcon={View}
            iconDescription="View documents"
            tooltipPosition="top"
            tooltipAlignment="end"
            onClick={() => setOpen(true)}
          />
        )}
      >
        {({open, setOpen}) => (
          <DocumentListModal
            open={open}
            setOpen={setOpen}
            documents={documents}
            isFullyLoaded
            isError={false}
            isLoading={false}
            variableName={modalTitleSuffix}
          />
        )}
      </ModalStateManager>
    </AttachmentsContainer>
  );
});

export {DocumentContent};
