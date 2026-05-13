/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tag} from '@carbon/react';
import {Document} from '@carbon/react/icons';
import type {DocumentRecognition} from './types';
import {
  DocumentLabel,
  DocumentFileName,
  DocumentMeta,
  DocumentRow,
  ListSummary,
} from './styled';
import {formatBytes} from './formatBytes';
import {isDocumentCorrupted, isDocumentExpired} from './expiry';

type Props = {
  recognition: DocumentRecognition;
};

const DocumentValueCell: React.FC<Props> = ({recognition}) => {
  if (recognition.kind === 'single') {
    const {document} = recognition;
    const size = formatBytes(document.metadata.size);
    const expired = isDocumentExpired(document);
    const corrupted = !expired && isDocumentCorrupted(document);
    return (
      <DocumentRow>
        <DocumentLabel>
          <Document size={16} aria-hidden />
          <DocumentFileName title={document.metadata.fileName}>
            {document.metadata.fileName}
          </DocumentFileName>
          {size !== '' && <DocumentMeta>{size}</DocumentMeta>}
          {expired && (
            <Tag
              type="red"
              size="sm"
              title="The file is past its retention period and is no longer available"
            >
              Expired
            </Tag>
          )}
          {corrupted && (
            <Tag
              type="red"
              size="sm"
              title="The file is unreadable — bytes appear to be corrupted"
            >
              Corrupted
            </Tag>
          )}
        </DocumentLabel>
      </DocumentRow>
    );
  }

  if (recognition.kind === 'list') {
    const {documents} = recognition;
    return (
      <DocumentRow>
        <DocumentLabel>
          <Document size={16} aria-hidden />
          <ListSummary>
            {documents.length} document{documents.length === 1 ? '' : 's'}
          </ListSummary>
        </DocumentLabel>
      </DocumentRow>
    );
  }

  if (recognition.kind === 'truncated-list') {
    const {partialCount} = recognition;
    return (
      <DocumentRow>
        <DocumentLabel>
          <Document size={16} aria-hidden />
          <ListSummary
            title="More documents may exist beyond the truncation threshold"
          >
            {partialCount}+ documents
          </ListSummary>
        </DocumentLabel>
      </DocumentRow>
    );
  }

  return null;
};

export {DocumentValueCell};
