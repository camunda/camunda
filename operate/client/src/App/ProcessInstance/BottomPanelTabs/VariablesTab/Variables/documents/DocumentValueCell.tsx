/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Document} from '@carbon/react/icons';
import {InlineJsonEditor} from 'modules/components/InlineJsonEditor';
import type {DocumentRecognition} from './types';
import {
  DocumentLabel,
  DocumentFileName,
  DocumentRow,
  EmbeddedContainer,
  EmbeddedSummary,
  ListSummary,
} from './styled';

type Props = {
  variableName: string;
  variableValue: string;
  recognition: DocumentRecognition;
};

const DocumentValueCell: React.FC<Props> = ({
  variableName,
  variableValue,
  recognition,
}) => {
  if (recognition.kind === 'single') {
    const {document} = recognition;
    return (
      <DocumentRow>
        <DocumentLabel>
          <Document size={16} aria-hidden />
          <DocumentFileName title={document.metadata.fileName}>
            {document.metadata.fileName}
          </DocumentFileName>
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

  if (recognition.kind === 'embedded') {
    const {documents} = recognition;
    return (
      <EmbeddedContainer>
        <EmbeddedSummary>
          <Document size={16} aria-hidden />
          Contains {documents.length} document
          {documents.length === 1 ? '' : 's'}
        </EmbeddedSummary>
        <InlineJsonEditor value={variableValue} label={variableName} readOnly />
      </EmbeddedContainer>
    );
  }

  return null;
};

export {DocumentValueCell};
