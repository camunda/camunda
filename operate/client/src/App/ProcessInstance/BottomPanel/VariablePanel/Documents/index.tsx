/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState} from 'react';
import {View as ViewIcon, Download} from '@carbon/react/icons';
import {Button} from '@carbon/react';
import {DocumentPreviewModal} from './DocumentPreviewModal';
import {formatBytes} from './utils';
import {ActionsContainer, Spacer, StructuredList, DocumentName, DocumentInfo} from './styled';

type Document = {
  id: string;
  name: string;
  type: string;
  size: number;
  path: string;
  canPreview?: boolean;
};

const ALL_DOCUMENTS: Document[] = [
  {
    id: '1',
    name: 'nature.jpg',
    type: 'image/jpeg',
    size: 354304, // 346KB in bytes
    path: '/nature.jpg',
    canPreview: true,
  },
  {
    id: '2',
    name: 'EN-Camunda-Compared-to-Alternatives-2024.pdf',
    type: 'application/pdf',
    size: 209920, // 205KB in bytes
    path: '/EN-Camunda-Compared-to-Alternatives-2024.pdf',
    canPreview: true,
  },
  {
    id: '3',
    name: 'process-data.json',
    type: 'application/json',
    size: 427, // Size in bytes
    path: '/process-data.json',
    canPreview: true,
  },
  {
    id: '4',
    name: '2025-State-of-Process-Orchestration-Automation-Report_EN.pdf',
    type: 'application/pdf',
    size: 62914560, // 60MB in bytes
    path: '/2025-State-of-Process-Orchestration-Automation-Report_EN.pdf',
    canPreview: false,
  },
  {
    id: '5',
    name: 'sample-data.zip',
    type: 'application/zip',
    size: 10240, // 10KB in bytes
    path: '/sample-data.zip',
    canPreview: false,
  },
  {
    id: '6',
    name: 'ai_agent_memory.json',
    type: 'application/json',
    size: 3657, // Size in bytes
    path: '/ai_agent_memory.json',
    canPreview: true,
  },
];

type DocumentsProps = {
  filterToMemory?: boolean;
};

const Documents: React.FC<DocumentsProps> = ({filterToMemory = false}) => {
  const [selectedDocument, setSelectedDocument] = useState<Document | null>(
    null,
  );
  const [isModalOpen, setIsModalOpen] = useState(false);

  const DOCUMENTS = filterToMemory
    ? ALL_DOCUMENTS.filter((doc) => doc.name === 'ai_agent_memory.json')
    : ALL_DOCUMENTS.filter((doc) => doc.name !== 'ai_agent_memory.json');

  const handleView = (document: Document) => {
    setSelectedDocument(document);
    setIsModalOpen(true);
  };

  const handleDownload = (doc: Document) => {
    // Create a link element and trigger download
    const link = window.document.createElement('a');
    link.href = doc.path;
    link.download = doc.name;
    window.document.body.appendChild(link);
    link.click();
    window.document.body.removeChild(link);
  };

  const rows = DOCUMENTS.map((doc) => ({
    key: doc.id,
    columns: [
      {
        cellContent: (
          <DocumentName title={doc.name}>
            {doc.name}
          </DocumentName>
        ),
        width: '40%',
      },
      {
        cellContent: (
          <DocumentInfo>
            {doc.type}
          </DocumentInfo>
        ),
        width: '25%',
      },
      {
        cellContent: (
          <DocumentInfo>
            {formatBytes(doc.size)}
          </DocumentInfo>
        ),
        width: '25%',
      },
      {
        cellContent: (
          <ActionsContainer>
            {doc.canPreview ? (
              <>
                <Button
                  kind="ghost"
                  size="sm"
                  tooltipPosition="left"
                  iconDescription={`View ${doc.name}`}
                  aria-label={`View ${doc.name}`}
                  onClick={() => handleView(doc)}
                  hasIconOnly
                  renderIcon={ViewIcon}
                />
                <Button
                  kind="ghost"
                  size="sm"
                  tooltipPosition="left"
                  iconDescription={`Download ${doc.name}`}
                  aria-label={`Download ${doc.name}`}
                  onClick={() => handleDownload(doc)}
                  hasIconOnly
                  renderIcon={Download}
                />
              </>
            ) : (
              <>
                <Spacer />
                <Button
                  kind="ghost"
                  size="sm"
                  tooltipPosition="left"
                  iconDescription={`Download ${doc.name}`}
                  aria-label={`Download ${doc.name}`}
                  onClick={() => handleDownload(doc)}
                  hasIconOnly
                  renderIcon={Download}
                />
              </>
            )}
          </ActionsContainer>
        ),
        width: '10%',
      },
    ],
  }));

  return (
    <>
      <StructuredList
        label="Documents"
        headerColumns={[
          {cellContent: 'Name', width: '40%'},
          {cellContent: 'Type', width: '25%'},
          {cellContent: 'Size', width: '25%'},
          {cellContent: '', width: '10%'},
        ]}
        headerSize="sm"
        verticalCellPadding="var(--cds-spacing-02)"
        rows={rows}
      />
      {selectedDocument && (
        <DocumentPreviewModal
          document={selectedDocument}
          isOpen={isModalOpen}
          onClose={() => {
            setIsModalOpen(false);
            setSelectedDocument(null);
          }}
        />
      )}
    </>
  );
};

export {Documents};
