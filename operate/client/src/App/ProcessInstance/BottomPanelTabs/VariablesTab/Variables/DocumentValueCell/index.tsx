/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tag} from '@carbon/react';
import {Document} from '@carbon/react/icons';
import {
  toHumanReadableBytes,
  type DocumentParseResult,
} from './parseDocumentVariable';
import {middleTruncate} from './middleTruncate';
import {
  DocumentCellContainer,
  DocumentIcon,
  DocumentFileName,
  DocumentSize,
  DocumentCount,
} from './styled';

type Props = {
  result: DocumentParseResult;
};

const DocumentValueCell: React.FC<Props> = ({result}) => {
  if (result.type === 'single') {
    const {fileName, size, isExpired} = result.document;
    return (
      <DocumentCellContainer data-testid="document-value-cell">
        <DocumentIcon>
          <Document size={16} />
        </DocumentIcon>
        <DocumentFileName title={fileName}>
          {middleTruncate(fileName)}
        </DocumentFileName>
        <DocumentSize>{toHumanReadableBytes(size)}</DocumentSize>
        {isExpired && (
          <Tag type="red" size="sm">
            Expired
          </Tag>
        )}
      </DocumentCellContainer>
    );
  }

  const count = result.documents.length;
  const suffix = count !== 1 ? 'documents' : 'document';
  const label = result.isLowerBound
    ? `${count}+ ${suffix}`
    : `${count} ${suffix}`;

  return (
    <DocumentCellContainer data-testid="document-value-cell">
      <DocumentIcon>
        <Document size={16} />
      </DocumentIcon>
      <DocumentCount>{label}</DocumentCount>
    </DocumentCellContainer>
  );
};

export {DocumentValueCell};
