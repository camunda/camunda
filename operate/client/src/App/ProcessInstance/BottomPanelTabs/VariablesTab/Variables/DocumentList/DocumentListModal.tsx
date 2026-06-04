/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {Modal} from '@carbon/react';
import type {StateProps} from 'modules/components/ModalStateManager';
import {useVariable} from 'modules/queries/variables/useVariable';
import {
  parseDocumentVariable,
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
  variableKey: string;
  variableName: string;
};

const DocumentListModal: React.FC<StateProps & Props> = ({
  open,
  setOpen,
  documents,
  isLowerBound,
  variableKey,
  variableName,
}) => {
  const {data, isSuccess, isError, isLoading} = useVariable(variableKey, {
    enabled: open && isLowerBound,
  });

  const resolvedDocuments = useMemo(() => {
    if (data?.value === undefined) {
      return documents;
    }

    const result = parseDocumentVariable(data.value, false);
    if (result === null) {
      return documents;
    }

    return result.type === 'list' ? result.documents : [result.document];
  }, [data?.value, documents]);

  const isFullyLoaded = !isLowerBound || isSuccess;
  const count = resolvedDocuments.length;
  const prefix = isFullyLoaded ? `${count}` : `${count}+`;
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
        {resolvedDocuments.map((document, index) => (
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
              document={document}
              variableName={variableName}
            />
          </DocumentListItem>
        ))}
      </DocumentList>
      {isLoading && (
        <TruncationNotice>
          Loading the full variable value... More documents may exist for this
          variable.
        </TruncationNotice>
      )}
      {isError && (
        <TruncationNotice>
          Failed to load the full variable value. More documents may exist for
          this variable.
        </TruncationNotice>
      )}
    </Modal>
  );
};

export {DocumentListModal};
