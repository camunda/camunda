/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Modal, Tag} from '@carbon/react';
import type {StateProps} from 'modules/components/ModalStateManager';
import type {DocumentInfo} from '../documentInfo';
import {toHumanReadableBytes} from '../humanReadableBytes';
import {middleTruncate} from '../middleTruncate';
import {PreviewDocumentButton} from '../DocumentPreview/PreviewDocumentButton';
import {DownloadDocumentButton} from '../DownloadDocumentButton';
import {
  DocumentList,
  DocumentListItem,
  DocumentInfo as DocumentInfoBlock,
  DocumentFileName,
  DocumentSize,
  TruncationNotice,
} from './styled';

type Props = {
  documents: DocumentInfo[];
  labelSuffix: string;
  loadingHint?: string;
  errorHint?: string;
};

const DocumentListModal: React.FC<StateProps & Props> = ({
  open,
  setOpen,
  documents,
  labelSuffix,
  loadingHint,
  errorHint,
}) => {
  const isFullyLoaded = !loadingHint && !errorHint;
  const count = documents.length;
  const prefix = isFullyLoaded ? `${count}` : `${count}+`;
  const suffix = count !== 1 ? 'documents' : 'document';

  return (
    <Modal
      open={open}
      onRequestClose={() => setOpen(false)}
      modalHeading={`${prefix} ${suffix} in ${labelSuffix}`}
      size="md"
      passiveModal
    >
      <DocumentList>
        {documents.map((document, index) => (
          <DocumentListItem
            aria-labelledby={`document-item-${index}`}
            key={index}
          >
            <DocumentInfoBlock>
              <DocumentFileName
                id={`document-item-${index}`}
                title={document.fileName}
              >
                {middleTruncate(document.fileName)}
              </DocumentFileName>
              <DocumentSize>{toHumanReadableBytes(document.size)}</DocumentSize>
              {document.isExpired && (
                <Tag type="red" size="sm">
                  Expired
                </Tag>
              )}
            </DocumentInfoBlock>
            <PreviewDocumentButton
              document={document}
              labelSuffix={labelSuffix}
            />
            <DownloadDocumentButton
              document={document}
              labelSuffix={labelSuffix}
            />
          </DocumentListItem>
        ))}
      </DocumentList>
      {loadingHint && <TruncationNotice>{loadingHint}</TruncationNotice>}
      {errorHint && <TruncationNotice>{errorHint}</TruncationNotice>}
    </Modal>
  );
};

export {DocumentListModal};
