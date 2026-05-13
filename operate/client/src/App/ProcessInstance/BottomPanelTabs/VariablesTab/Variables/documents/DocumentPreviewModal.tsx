/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense, useEffect, useState} from 'react';
import {InlineNotification, Modal} from '@carbon/react';
import {readFile} from './readFile';
import type {DocumentReference} from './types';
import {
  MOCK_DOCUMENT_ASSET_PATHS,
  MOCK_CORRUPTED_DOCUMENT_IDS,
} from './mockDocumentVariables';
import {PreviewIframeContainer, PreviewImageContainer} from './styled';

const JSONEditorLazy = lazy(async () => {
  const [{loadMonaco}, {JSONEditor}] = await Promise.all([
    import('modules/loadMonaco'),
    import('modules/components/JSONEditor'),
  ]);

  loadMonaco();

  return {default: JSONEditor};
});

type Props = {
  document: DocumentReference;
  isOpen: boolean;
  onClose: () => void;
};

// TODO: Replace MOCK_DOCUMENT_ASSET_PATHS lookup with API call to
// GET /v2/documents/{documentId} that returns a binary stream / signed URL.
function resolveAssetPath(document: DocumentReference): string | null {
  return MOCK_DOCUMENT_ASSET_PATHS[document.documentId] ?? null;
}

const DocumentPreviewModal: React.FC<Props> = ({document, isOpen, onClose}) => {
  const [content, setContent] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  const fileName = document.metadata.fileName;
  const contentType = document.metadata.contentType ?? '';
  const assetPath = resolveAssetPath(document);
  const isPDF = contentType === 'application/pdf';
  const isImage = contentType.startsWith('image/');
  const isJSON = contentType === 'application/json';

  useEffect(() => {
    if (!isOpen || !isJSON || !assetPath) {
      return;
    }
    setIsLoading(true);
    setLoadError(null);
    readFile(assetPath)
      .then((text) => setContent(text))
      .catch((error) => {
        setLoadError(
          error instanceof Error ? error.message : 'Failed to load file',
        );
      })
      .finally(() => setIsLoading(false));
  }, [isOpen, isJSON, assetPath]);

  useEffect(() => {
    if (!isOpen) {
      setContent(null);
      setLoadError(null);
    }
  }, [isOpen]);

  if (!isOpen) {
    return null;
  }

  let modalContent: React.ReactNode = null;

  if (MOCK_CORRUPTED_DOCUMENT_IDS.has(document.documentId)) {
    modalContent = (
      <InlineNotification
        kind="error"
        title="Could not display this file"
        subtitle="The file appears to be corrupted. You can still download it to inspect the raw bytes."
        hideCloseButton
        lowContrast
      />
    );
  } else if (assetPath === null) {
    modalContent = (
      <InlineNotification
        kind="warning"
        title="Preview not available"
        subtitle="No asset is wired to this document reference in the prototype."
        hideCloseButton
        lowContrast
      />
    );
  } else if (loadError !== null) {
    modalContent = (
      <InlineNotification
        kind="error"
        title="Could not load document"
        subtitle={loadError}
        hideCloseButton
        lowContrast
      />
    );
  } else if (isPDF) {
    modalContent = (
      <PreviewIframeContainer>
        <iframe src={assetPath} title={fileName} />
      </PreviewIframeContainer>
    );
  } else if (isImage) {
    modalContent = (
      <PreviewImageContainer>
        <img src={assetPath} alt={fileName} />
      </PreviewImageContainer>
    );
  } else if (isJSON) {
    if (isLoading || content === null) {
      modalContent = <div>Loading...</div>;
    } else {
      modalContent = (
        <Suspense fallback={<div>Loading editor…</div>}>
          <JSONEditorLazy value={content} readOnly height="70vh" />
        </Suspense>
      );
    }
  } else {
    modalContent = (
      <InlineNotification
        kind="info"
        title="Preview not available"
        subtitle={`No inline preview for ${contentType || 'this file type'}.`}
        hideCloseButton
        lowContrast
      />
    );
  }

  return (
    <Modal
      open={isOpen}
      modalHeading={fileName}
      onRequestClose={onClose}
      passiveModal
      size="lg"
    >
      {modalContent}
    </Modal>
  );
};

export {DocumentPreviewModal};
