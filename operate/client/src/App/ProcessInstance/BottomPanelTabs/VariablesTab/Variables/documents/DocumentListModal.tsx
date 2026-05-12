/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Button, Modal} from '@carbon/react';
import {Download, View as ViewIcon} from '@carbon/react/icons';
import type {DocumentReference} from './types';
import {DocumentPreviewModal} from './DocumentPreviewModal';
import {downloadDocument} from './downloadDocument';
import {formatBytes} from './formatBytes';
import {
  ModalListItem,
  ModalListItemFileName,
  ModalListItemLabel,
  ModalListItemMeta,
} from './styled';

type Props = {
  variableName: string;
  documents: DocumentReference[];
  isOpen: boolean;
  onClose: () => void;
};

const DocumentListModal: React.FC<Props> = ({
  variableName,
  documents,
  isOpen,
  onClose,
}) => {
  const [previewDocument, setPreviewDocument] =
    useState<DocumentReference | null>(null);

  return (
    <>
      <Modal
        open={isOpen}
        modalHeading={`${documents.length} document${documents.length === 1 ? '' : 's'} in ${variableName}`}
        onRequestClose={onClose}
        passiveModal
        size="sm"
      >
        {documents.map((document, index) => {
          const meta = formatBytes(document.metadata.size);

          return (
            <ModalListItem
              key={document.documentId}
              $lastItem={index === documents.length - 1}
              data-testid={`document-list-item-${document.documentId}`}
            >
              <ModalListItemLabel>
                <ModalListItemFileName>
                  {document.metadata.fileName}
                </ModalListItemFileName>
                {meta !== '' && <ModalListItemMeta>{meta}</ModalListItemMeta>}
              </ModalListItemLabel>
              <div>
                <Button
                  kind="ghost"
                  size="sm"
                  hasIconOnly
                  iconDescription="View"
                  tooltipPosition="top"
                  renderIcon={ViewIcon}
                  onClick={() => setPreviewDocument(document)}
                  data-testid={`view-document-${document.documentId}-button`}
                />
                <Button
                  kind="ghost"
                  size="sm"
                  hasIconOnly
                  iconDescription="Download"
                  tooltipPosition="top"
                  renderIcon={Download}
                  onClick={() => downloadDocument(document)}
                  data-testid={`download-document-${document.documentId}-button`}
                />
              </div>
            </ModalListItem>
          );
        })}
      </Modal>
      {previewDocument !== null && (
        <DocumentPreviewModal
          document={previewDocument}
          isOpen
          onClose={() => setPreviewDocument(null)}
        />
      )}
    </>
  );
};

export {DocumentListModal};
