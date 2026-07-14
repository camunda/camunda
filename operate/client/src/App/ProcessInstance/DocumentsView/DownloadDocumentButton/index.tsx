/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, Tooltip} from '@carbon/react';
import {Download} from '@carbon/react/icons';
import type {DocumentInfo} from '../documentInfo';
import {TooltipTrigger} from './styled';
import {tracking} from 'modules/tracking';

function getDisabledTooltipText(document: DocumentInfo): string {
  return document.isExpired ? 'Document has expired' : 'Download unavailable';
}

type Props = {
  document: DocumentInfo;
  variableName: string;
};

const DownloadDocumentButton: React.FC<Props> = ({document, variableName}) => {
  const isDisabled = document.link === null || document.isExpired;

  const button = (
    <Button
      as={isDisabled ? undefined : 'a'}
      href={document.link ?? undefined}
      download={isDisabled ? undefined : document.fileName}
      rel="noopener"
      kind="ghost"
      size="sm"
      hasIconOnly
      renderIcon={Download}
      iconDescription="Download"
      tooltipPosition="top"
      tooltipAlignment="end"
      aria-label={`Download document for variable ${variableName}`}
      disabled={isDisabled}
      onClick={() => {
        tracking.track({
          eventName: 'document-downloaded',
          documentType: document.type,
          contentType: document.contentType,
          size: document.size,
        });
      }}
    />
  );

  if (!isDisabled) {
    return button;
  }

  return (
    <Tooltip
      label={getDisabledTooltipText(document)}
      align="top-end"
      enterDelayMs={0}
      leaveDelayMs={0}
      className="cds--icon-tooltip"
    >
      <TooltipTrigger tabIndex={0}>{button}</TooltipTrigger>
    </Tooltip>
  );
};

export {DownloadDocumentButton};
