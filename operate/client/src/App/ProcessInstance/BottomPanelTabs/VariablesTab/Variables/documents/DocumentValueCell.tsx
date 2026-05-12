/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

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

type Props = {
  recognition: DocumentRecognition;
};

const DocumentValueCell: React.FC<Props> = ({recognition}) => {
  if (recognition.kind === 'single') {
    const {document} = recognition;
    const size = formatBytes(document.metadata.size);
    return (
      <DocumentRow>
        <DocumentLabel>
          <Document size={16} aria-hidden />
          <DocumentFileName title={document.metadata.fileName}>
            {document.metadata.fileName}
          </DocumentFileName>
          {size !== '' && <DocumentMeta>{size}</DocumentMeta>}
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
