/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect} from 'preact/hooks';
import {jsx, jsxs} from 'preact/jsx-runtime';
import {JSONViewerPreact} from './JSONViewerPreact';
import {TextViewerPreact} from './TextViewerPreact';

const type = 'documentPreview';

// Custom hook to fetch document content for preview
function useDocumentContent(endpoint: string, contentType: string) {
  const [content, setContent] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!endpoint || (!isJSONContentType(contentType) && !isTextContentType(contentType))) {
      return;
    }

    setLoading(true);
    setError(null);

    fetch(endpoint)
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`Failed to fetch document: ${response.status}`);
        }
        return response.text();
      })
      .then((text) => {
        setContent(text);
      })
      .catch((err) => {
        setError(err.message);
      })
      .finally(() => {
        setLoading(false);
      });
  }, [endpoint, contentType]);

  return {content, loading, error};
}

function isJSONContentType(contentType: string): boolean {
  return contentType.toLowerCase() === 'application/json';
}

function isTextContentType(contentType: string): boolean {
  return contentType.toLowerCase().startsWith('text/');
}

// Custom DocumentRenderer component that supports JSON and text preview
function CustomDocumentRenderer(props: any) {
  const {documentMetadata, endpoint, maxHeight, domId} = props;
  const {metadata} = documentMetadata;
  const [hasError, setHasError] = useState(false);

  const contentType = metadata?.contentType?.toLowerCase() || '';
  const fileName = metadata?.fileName || 'Unknown file';
  
  const {content, loading, error} = useDocumentContent(endpoint, contentType);

  const singleDocumentContainerClassName = `fjs-${type}-single-document-container`;
  const errorMessageId = `${domId}-error-message`;
  const errorMessage = error || 'Unable to download document';

  // Handle images (original form-js behavior)
  if (contentType.startsWith('image/')) {
    return jsxs('div', {
      class: singleDocumentContainerClassName,
      style: {maxHeight},
      'aria-describedby': hasError ? errorMessageId : undefined,
      children: [
        jsx('img', {
          src: endpoint,
          alt: fileName,
          class: `fjs-${type}-image`,
        }),
        jsx(DownloadButton, {
          endpoint: endpoint,
          fileName: fileName,
          onDownloadError: () => setHasError(true),
        }),
        hasError && jsx('div', {
          id: errorMessageId,
          class: 'fjs-form-field-error',
          children: errorMessage,
        }),
      ],
    });
  }

  // Handle PDFs (original form-js behavior - fallback to download for now)
  // Could be enhanced to include PDF renderer later
  if (contentType === 'application/pdf') {
    // For now, fallback to download since we'd need to import PdfRenderer from form-js
  }

  // Handle JSON files
  if (isJSONContentType(contentType)) {
    if (loading) {
      return jsx('div', {
        class: singleDocumentContainerClassName,
        style: {maxHeight},
        children: jsxs('div', {
          children: [
            jsx('div', {
              class: 'fjs-document-preview-title',
              children: fileName,
            }),
            jsx('div', {
              children: 'Loading JSON preview...',
            }),
          ],
        }),
      });
    }

    if (error) {
      return jsxs('div', {
        class: singleDocumentContainerClassName,
        style: {maxHeight},
        'aria-describedby': errorMessageId,
        children: [
          jsx('div', {
            class: 'fjs-document-preview-title',
            children: fileName,
          }),
          jsx('div', {
            id: errorMessageId,
            class: 'fjs-form-field-error',
            children: errorMessage,
          }),
          jsx(DownloadButton, {
            endpoint: endpoint,
            fileName: fileName,
            onDownloadError: () => setHasError(true),
          }),
        ],
      });
    }

    return jsxs('div', {
      class: singleDocumentContainerClassName,
      style: {maxHeight},
      children: [
        jsx('div', {
          class: 'fjs-document-preview-title',
          children: fileName,
        }),
        jsx(JSONViewerPreact, {
          value: content!,
          height: maxHeight ? `${maxHeight}px` : '300px',
          'data-testid': 'json-document-preview',
        }),
        jsx(DownloadButton, {
          endpoint: endpoint,
          fileName: fileName,
          onDownloadError: () => setHasError(true),
        }),
      ],
    });
  }

  // Handle text files
  if (isTextContentType(contentType)) {
    if (loading) {
      return jsx('div', {
        class: singleDocumentContainerClassName,
        style: {maxHeight},
        children: jsxs('div', {
          children: [
            jsx('div', {
              class: 'fjs-document-preview-title',
              children: fileName,
            }),
            jsx('div', {
              children: 'Loading text preview...',
            }),
          ],
        }),
      });
    }

    if (error) {
      return jsxs('div', {
        class: singleDocumentContainerClassName,
        style: {maxHeight},
        'aria-describedby': errorMessageId,
        children: [
          jsx('div', {
            class: 'fjs-document-preview-title',
            children: fileName,
          }),
          jsx('div', {
            id: errorMessageId,
            class: 'fjs-form-field-error',
            children: errorMessage,
          }),
          jsx(DownloadButton, {
            endpoint: endpoint,
            fileName: fileName,
            onDownloadError: () => setHasError(true),
          }),
        ],
      });
    }

    return jsxs('div', {
      class: singleDocumentContainerClassName,
      style: {maxHeight},
      children: [
        jsx('div', {
          class: 'fjs-document-preview-title',
          children: fileName,
        }),
        jsx(TextViewerPreact, {
          value: content!,
          height: maxHeight ? `${maxHeight}px` : '300px',
          'data-testid': 'text-document-preview',
        }),
        jsx(DownloadButton, {
          endpoint: endpoint,
          fileName: fileName,
          onDownloadError: () => setHasError(true),
        }),
      ],
    });
  }

  // Fallback to original behavior (download button only)
  return jsxs('div', {
    class: `fjs-${type}-non-preview-item ${singleDocumentContainerClassName}`,
    'aria-describedby': hasError ? errorMessageId : undefined,
    children: [
      jsxs('div', {
        children: [
          jsx('div', {
            class: 'fjs-document-preview-title',
            children: fileName,
          }),
          hasError && jsx('div', {
            id: errorMessageId,
            class: 'fjs-form-field-error',
            children: errorMessage,
          }),
        ],
      }),
      jsx(DownloadButton, {
        endpoint: endpoint,
        fileName: fileName,
        onDownloadError: () => setHasError(true),
      }),
    ],
  });
}

// Simple download button implementation (replicating form-js behavior)
function DownloadButton(props: {
  endpoint: string;
  fileName: string;
  onDownloadError: () => void;
}) {
  const {endpoint, fileName, onDownloadError} = props;

  const handleDownload = async () => {
    try {
      const response = await fetch(endpoint);
      if (!response.ok) {
        onDownloadError();
        return;
      }
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = fileName;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch {
      onDownloadError();
    }
  };

  return jsx('button', {
    type: 'button',
    class: 'fjs-button fjs-button-secondary',
    onClick: handleDownload,
    children: 'Download',
  });
}

CustomDocumentRenderer.config = {
  type,
  keyed: false,
  group: 'presentation',
  name: 'Document preview',
  create: (options = {}) => ({
    label: 'Document preview',
    ...options,
  }),
};

export {CustomDocumentRenderer};