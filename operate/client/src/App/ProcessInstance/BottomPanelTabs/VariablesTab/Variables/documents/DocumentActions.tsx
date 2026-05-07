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
import {downloadDocument, downloadVariableAsJson} from './downloadDocument';
import type {DocumentRecognition} from './types';

type Props = {
  variableName: string;
  variableValue: string;
  recognition: DocumentRecognition;
};

const DocumentActions: React.FC<Props> = ({
  variableName,
  variableValue,
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

  if (recognition.kind === 'embedded') {
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
        <Button
          kind="ghost"
          size="sm"
          hasIconOnly
          iconDescription="Download"
          tooltipPosition="top"
          renderIcon={Download}
          onClick={() => downloadVariableAsJson(variableName, variableValue)}
          data-testid={`download-document-${variableName}-button`}
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

  return null;
};

export {DocumentActions};
