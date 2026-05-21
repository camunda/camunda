/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Document} from '@carbon/react/icons';
import {
  toHumanReadableBytes,
  type DocumentParseResult,
} from './parseDocumentVariable';
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

const TRUNCATION_LIMIT = 60;

function middleTruncate(text: string, limit: number): string {
  if (text.length <= limit) {
    return text;
  }
  const half = Math.floor((limit - 1) / 2);
  return text.slice(0, half) + '\u2026' + text.slice(text.length - half);
}

const DocumentValueCell: React.FC<Props> = ({result}) => {
  if (result.type === 'single') {
    const {fileName, size} = result.document;
    return (
      <DocumentCellContainer data-testid="document-value-cell">
        <DocumentIcon>
          <Document size={16} />
        </DocumentIcon>
        <DocumentFileName title={fileName}>
          {middleTruncate(fileName, TRUNCATION_LIMIT)}
        </DocumentFileName>
        {size !== undefined && (
          <DocumentSize>{toHumanReadableBytes(size)}</DocumentSize>
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
