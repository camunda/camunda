/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Modal} from '@carbon/react';
import type {StateProps} from 'modules/components/ModalStateManager';
import {
  toHumanReadableBytes,
  type DocumentInfo,
} from '../DocumentValueCell/parseDocumentVariable';
import {middleTruncate} from '../DocumentValueCell/middleTruncate';
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
  isLowerBound: boolean;
  variableName: string;
};

const DocumentListModal: React.FC<StateProps & Props> = ({
  open,
  setOpen,
  documents,
  isLowerBound,
  variableName,
}) => {
  const count = documents.length;
  const prefix = isLowerBound ? `${count}+` : `${count}`;
  const suffix = count !== 1 ? 'documents' : 'document';

  return (
    <Modal
      open={open}
      onRequestClose={() => setOpen(false)}
      modalHeading={`${prefix} ${suffix} in ${variableName}`}
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
              {document.size !== undefined && (
                <DocumentSize>
                  {toHumanReadableBytes(document.size)}
                </DocumentSize>
              )}
            </DocumentInfoBlock>
            <PreviewDocumentButton
              document={document}
              variableName={variableName}
            />
            <DownloadDocumentButton
              fileName={document.fileName}
              documentLink={document.link}
              variableName={variableName}
            />
          </DocumentListItem>
        ))}
      </DocumentList>
      {isLowerBound && (
        <TruncationNotice>
          More documents may exist for this variable.
        </TruncationNotice>
      )}
    </Modal>
  );
};

export {DocumentListModal};
