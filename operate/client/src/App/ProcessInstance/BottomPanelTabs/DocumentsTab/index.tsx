/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {IconButton, Layer, Loading} from '@carbon/react';
import {Download} from '@carbon/react/icons';
import styled from 'styled-components';
import {StructuredList} from 'modules/components/StructuredList';
import {EmptyMessage} from 'modules/components/EmptyMessage';
import {ErrorMessage} from 'modules/components/ErrorMessage';
import {useDocuments} from 'modules/queries/variables/useDocuments';
import {fetchDocument} from 'modules/api/v2/documentReferences/fetchDocument';
import {type DocumentReferenceSearchResult} from '@camunda/camunda-api-zod-schemas/8.10';

const Content = styled(Layer)`
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 0 var(--cds-spacing-05);
`;

const ScrollContainer = styled.div`
  height: 100%;
  overflow-y: auto;
`;

const CenteredContainer = styled.div`
  display: flex;
  height: 100%;
  justify-content: center;
  align-items: center;
`;

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
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

  if (isError) {
    return (
      <Content>
        <CenteredContainer>
          <ErrorMessage
            message="Documents could not be fetched"
            additionalInfo="Refresh the page to try again"
          />
        </CenteredContainer>
      </Content>
    );
  }

  return (
    <Content>
      {!isLoading && documents.length === 0 ? (
        <CenteredContainer>
          <EmptyMessage message="No document references found for this process instance" />
        </CenteredContainer>
      ) : (
        <ScrollContainer>
          <StructuredList
            dataTestId="documents-list"
            headerColumns={[
              {cellContent: 'Variable', width: '25%'},
              {cellContent: 'File name', width: '35%'},
              {cellContent: 'File type', width: '20%'},
              {cellContent: 'Size', width: '15%'},
              {cellContent: '', width: '5%'},
            ]}
            headerSize="sm"
            verticalCellPadding="var(--cds-spacing-02)"
            label="Documents List"
            onVerticalScrollStartReach={() => {
              if (hasPreviousPage) fetchPreviousPage();
            }}
            onVerticalScrollEndReach={() => {
              if (hasNextPage) fetchNextPage();
            }}
            rows={documents.map((doc) => ({
              key: doc.variableKey,
              dataTestId: doc.variableKey,
              columns: [
                {cellContent: doc.variableName ?? '—'},
                {cellContent: doc.fileName ?? '—'},
                {cellContent: doc.contentType ?? '—'},
                {cellContent: doc.size !== null ? formatSize(doc.size) : '—'},
                {
                  cellContent: (
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
                },
              ],
            }))}
          />
        </ScrollContainer>
      )}
    </Content>
  );
};

export {DocumentsTab};
