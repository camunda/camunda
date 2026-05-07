/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useState} from 'react';
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
  {header: 'Variable', key: 'variableName'},
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

function sortDocuments(
  docs: DocumentReferenceSearchResult[],
  sortBy: string,
  sortOrder: 'asc' | 'desc',
): DocumentReferenceSearchResult[] {
  return [...docs].sort((a, b) => {
    let aVal: string | number | null;
    let bVal: string | number | null;

    switch (sortBy) {
      case 'variableName':
        aVal = a.variableName;
        bVal = b.variableName;
        break;
      case 'fileName':
        aVal = a.fileName;
        bVal = b.fileName;
        break;
      default:
        return 0;
    }

    if (aVal === null && bVal === null) return 0;
    if (aVal === null) return 1;
    if (bVal === null) return -1;

    const cmp = String(aVal).localeCompare(String(bVal));
    return sortOrder === 'asc' ? cmp : -cmp;
  });
}

const DocumentsTab: React.FC = () => {
  const {
    documents,
    isLoading,
    isError,
    fetchNextPage,
    fetchPreviousPage,
    hasNextPage,
    hasPreviousPage,
  } = useDocuments();

  const [downloadingId, setDownloadingId] = useState<string | null>(null);
  const location = useLocation();

  const sortedDocuments = useMemo(() => {
    const sortParams = getSortParams(location.search);
    if (sortParams === null) return documents;
    return sortDocuments(documents, sortParams.sortBy, sortParams.sortOrder);
  }, [documents, location.search]);

  const tableState = isError
    ? ('error' as const)
    : isLoading
      ? ('skeleton' as const)
      : sortedDocuments.length === 0
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
          if (hasPreviousPage) fetchPreviousPage();
        }}
        onVerticalScrollEndReach={() => {
          if (hasNextPage) fetchNextPage();
        }}
        rows={sortedDocuments.map((doc) => ({
          id: doc.variableKey,
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
