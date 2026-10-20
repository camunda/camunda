/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {memo, useId} from 'react';
import type {AgentInstanceHistoryItem} from '@camunda/camunda-api-zod-schemas/8.10';
import {Document} from '@carbon/react/icons';
import {
  AttachmentsLabel,
  DocumentAttachments,
  DocumentList,
  DocumentCard,
  DocumentPreview,
  DocumentFileName,
  DocumentFooter,
} from './styled';
import {toDocumentInfo} from 'App/ProcessInstance/DocumentsView/documentInfo';
import {DownloadDocumentButton} from 'App/ProcessInstance/DocumentsView/DownloadDocumentButton';
import {toHumanReadableBytes} from 'App/ProcessInstance/DocumentsView/humanReadableBytes';

const MAX_VISIBLE_DOCUMENTS = 4;

type ContentItem = AgentInstanceHistoryItem['content'][number];

type Props = {
  content: ContentItem[];
  modalTitleSuffix: string;
  showAll?: boolean;
};

const DocumentContent: React.FC<Props> = memo(function DocumentContent({
  content,
  modalTitleSuffix,
  showAll = false,
}) {
  const id = useId();

  const documents = content
    .filter((entry) => entry.contentType === 'DOCUMENT')
    .map(({documentReference}) => toDocumentInfo(documentReference));

  if (documents.length === 0) {
    return null;
  }

  const visibleDocuments = showAll
    ? documents
    : documents.slice(0, MAX_VISIBLE_DOCUMENTS);

  return (
    <DocumentAttachments>
      <AttachmentsLabel id={id}>
        Documents
        {visibleDocuments.length < documents.length &&
          ` (${visibleDocuments.length} of ${documents.length})`}
      </AttachmentsLabel>
      <DocumentList aria-labelledby={id}>
        {visibleDocuments.map((document, i) => (
          <DocumentCard key={i} aria-label={document.fileName}>
            <DocumentPreview
              document={document}
              labelSuffix={document.fileName}
            >
              <Document size={32} aria-hidden="true" />
              <DocumentFileName title={document.fileName}>
                {document.fileName}
              </DocumentFileName>
            </DocumentPreview>
            <DocumentFooter>
              <span>{toHumanReadableBytes(document.size)}</span>
              <DownloadDocumentButton
                document={document}
                labelSuffix={`${document.fileName} in ${modalTitleSuffix}`}
              />
            </DocumentFooter>
          </DocumentCard>
        ))}
      </DocumentList>
    </DocumentAttachments>
  );
});

export {DocumentContent};
