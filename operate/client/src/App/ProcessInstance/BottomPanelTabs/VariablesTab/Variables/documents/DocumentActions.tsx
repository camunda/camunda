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
import {ViewFullVariableButton} from '../ViewFullVariableButton';
import {isDocumentCorrupted, isDocumentExpired} from './expiry';
import type {DocumentRecognition} from './types';

type Props = {
  variableKey: string;
  variableName: string;
  variableValue: string;
  recognition: DocumentRecognition;
  canEdit?: boolean;
};

const DocumentActions: React.FC<Props> = ({
  variableKey,
  variableName,
  variableValue,
  recognition,
  canEdit = false,
}) => {
  const [isPreviewOpen, setIsPreviewOpen] = useState(false);
  const [isListOpen, setIsListOpen] = useState(false);

  const expandButton = (
    <ViewFullVariableButton
      mode="show"
      variableName={variableName}
      variableKey={variableKey}
      variableValue={variableValue}
      canEdit={canEdit}
    />
  );

  if (recognition.kind === 'single') {
    const {document} = recognition;
    const expired = isDocumentExpired(document);
    const corrupted = !expired && isDocumentCorrupted(document);
    // Expired (file purged) and corrupted (bytes unreadable) both leave the
    // reference intact but the file unusable — only the Expand button stays
    // so the operator can still inspect the JSON.
    const isUnusable = expired || corrupted;
    const showPreview = !isUnusable;
    const showDownload = !isUnusable;
    return (
      <>
        {expandButton}
        {showPreview && (
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
        )}
        {showDownload && (
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
        )}
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
        {expandButton}
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
        {expandButton}
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
