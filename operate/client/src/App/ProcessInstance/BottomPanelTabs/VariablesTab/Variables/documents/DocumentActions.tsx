/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Button} from '@carbon/react';
import {Download, View as ViewIcon} from '@carbon/react/icons';
import {DocumentPreviewModal} from './DocumentPreviewModal';
import {DocumentListModal} from './DocumentListModal';
import {downloadDocument} from './downloadDocument';
import {MOCK_TRUNCATED_FULL_DOCUMENTS} from './mockDocumentVariables';
import type {DocumentRecognition} from './types';

type Props = {
  variableKey: string;
  variableName: string;
  recognition: DocumentRecognition;
};

const DocumentActions: React.FC<Props> = ({
  variableKey,
  variableName,
  recognition,
}) => {
  const [isPreviewOpen, setIsPreviewOpen] = useState(false);
  const [isListOpen, setIsListOpen] = useState(false);

  if (recognition.kind === 'single') {
    const {document} = recognition;
    return (
      <>
        <Button
          kind="ghost"
          size="sm"
          hasIconOnly
          iconDescription="View"
          tooltipPosition="top"
          renderIcon={ViewIcon}
          onClick={() => setIsPreviewOpen(true)}
          data-testid={`view-document-${variableName}-button`}
        />
        <Button
          kind="ghost"
          size="sm"
          hasIconOnly
          iconDescription="Download"
          tooltipPosition="top"
          renderIcon={Download}
          onClick={() => downloadDocument(document)}
          data-testid={`download-document-${variableName}-button`}
        />
        {isPreviewOpen && (
          <DocumentPreviewModal
            document={document}
            isOpen
            onClose={() => setIsPreviewOpen(false)}
          />
        )}
      </>
    );
  }

  if (recognition.kind === 'list') {
    const {documents} = recognition;
    return (
      <>
        <Button
          kind="ghost"
          size="sm"
          hasIconOnly
          iconDescription="View documents"
          tooltipPosition="top"
          renderIcon={ViewIcon}
          onClick={() => setIsListOpen(true)}
          data-testid={`view-documents-${variableName}-button`}
        />
        {isListOpen && (
          <DocumentListModal
            variableName={variableName}
            documents={documents}
            isOpen
            onClose={() => setIsListOpen(false)}
          />
        )}
      </>
    );
  }

  if (recognition.kind === 'truncated-list') {
    // Prototype: full list comes from the mock side-channel. In production
    // this branch would lazy-fetch the full variable value and re-parse it.
    const fullDocuments =
      MOCK_TRUNCATED_FULL_DOCUMENTS[variableKey] ?? [];
    return (
      <>
        <Button
          kind="ghost"
          size="sm"
          hasIconOnly
          iconDescription="View documents"
          tooltipPosition="top"
          renderIcon={ViewIcon}
          onClick={() => setIsListOpen(true)}
          data-testid={`view-documents-${variableName}-button`}
        />
        {isListOpen && (
          <DocumentListModal
            variableName={variableName}
            documents={fullDocuments}
            isOpen
            onClose={() => setIsListOpen(false)}
          />
        )}
      </>
    );
  }

  return null;
};

export {DocumentActions};
