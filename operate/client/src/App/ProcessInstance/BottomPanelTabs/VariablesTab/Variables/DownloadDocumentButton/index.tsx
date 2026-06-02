/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@carbon/react';
import {Download} from '@carbon/react/icons';
import type {DocumentInfo} from '../DocumentValueCell/parseDocumentVariable';
import {tracking} from 'modules/tracking';

type Props = {
  document: DocumentInfo;
  variableName: string;
};

const DownloadDocumentButton: React.FC<Props> = ({document, variableName}) => {
  return (
    <Button
      as="a"
      href={document.link}
      download={document.fileName}
      rel="noopener"
      kind="ghost"
      size="sm"
      hasIconOnly
      renderIcon={Download}
      iconDescription="Download"
      tooltipPosition="top"
      tooltipAlignment="end"
      // @ts-expect-error - Solves rendering issues in `DocumentListModal`. Not exposed through TS but used at runtime.
      autoAlign={true}
      aria-label={`Download document for variable ${variableName}`}
      onClick={() => {
        tracking.track({
          eventName: 'document-downloaded',
          documentType: document.type,
          contentType: document.contentType ?? null,
          size: document.size ?? null,
        });
      }}
    />
  );
};

export {DownloadDocumentButton};
