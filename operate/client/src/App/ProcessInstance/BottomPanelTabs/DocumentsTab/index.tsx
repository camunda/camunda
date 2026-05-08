/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useLocation} from 'react-router-dom';
import {IconButton, Layer, Loading} from '@carbon/react';
import {Download} from '@carbon/react/icons';
import styled from 'styled-components';
import {SortableTable} from 'modules/components/SortableTable';
import {useDocuments} from 'modules/queries/variables/useDocuments';
import {fetchDocument} from 'modules/api/v2/documentReferences/fetchDocument';
import {getSortParams} from 'modules/utils/filter';
import {type DocumentReferenceSearchResult} from '@camunda/camunda-api-zod-schemas/8.10';

const Content = styled(Layer)`
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
`;

const HEADER_COLUMNS = [
  {header: 'Variable', key: 'variableName', isDisabled: true},
  {header: 'File name', key: 'fileName'},
  {header: 'File type', key: 'contentType', isDisabled: true},
  {header: 'Size', key: 'size', isDisabled: true},
  {header: '', key: 'download', isDisabled: true},
];

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

const DocumentsTab: React.FC = () => {
  const location = useLocation();
  const sortParams = getSortParams(location.search);

  const {
    documents,
    isLoading,
    isError,
    fetchNextPage,
    fetchPreviousPage,
    hasNextPage,
    hasPreviousPage,
    isFetchingNextPage,
    isFetchingPreviousPage,
  } = useDocuments(sortParams ?? undefined);

  const [downloadingId, setDownloadingId] = useState<string | null>(null);

  const tableState = isError
    ? ('error' as const)
    : isLoading
      ? ('skeleton' as const)
      : documents.length === 0
        ? ('empty' as const)
        : ('content' as const);

  const handleDownload = async (doc: DocumentReferenceSearchResult) => {
    if (downloadingId !== null) return;
    setDownloadingId(doc.documentId);
    try {
      const response = await fetchDocument({
        documentId: doc.documentId,
        storeId: doc.storeId ?? undefined,
        contentHash: doc.contentHash ?? undefined,
      });
      if (response.ok) {
        const blob = await response.blob();
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = doc.fileName ?? doc.documentId;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
      }
    } catch {
      // download failed silently
    } finally {
      setDownloadingId(null);
    }
  };

  return (
    <Content>
      <SortableTable
        state={tableState}
        emptyMessage={{
          message: 'No document references found for this process instance',
        }}
        headerColumns={HEADER_COLUMNS}
        columnsWithNoContentPadding={['download']}
        onVerticalScrollStartReach={() => {
          if (hasPreviousPage && !isFetchingPreviousPage) fetchPreviousPage();
        }}
        onVerticalScrollEndReach={() => {
          if (hasNextPage && !isFetchingNextPage) fetchNextPage();
        }}
        rows={documents.map((doc) => ({
          id: doc.documentId,
          variableName: doc.variableName ?? '—',
          fileName: doc.fileName ?? '—',
          contentType: doc.contentType ?? '—',
          size: doc.size !== null ? formatSize(doc.size) : '—',
          download: (
            <IconButton
              kind="ghost"
              size="sm"
              label="Download"
              aria-label={`Download ${doc.fileName ?? doc.documentId}`}
              disabled={downloadingId !== null}
              onClick={() => handleDownload(doc)}
            >
              {downloadingId === doc.documentId ? (
                <Loading withOverlay={false} small />
              ) : (
                <Download />
              )}
            </IconButton>
          ),
        }))}
      />
    </Content>
  );
};

export {DocumentsTab};
