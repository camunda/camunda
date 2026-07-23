/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, Tooltip} from '@carbon/react';
import {View} from '@carbon/react/icons';
import {ModalStateManager} from 'modules/components/ModalStateManager';
import type {DocumentInfo} from '../documentInfo';
import {DocumentPreviewModal} from './DocumentPreviewModal';
import {TooltipTrigger} from './styled';
import {tracking} from 'modules/tracking';

function getDisabledTooltipText(document: DocumentInfo): string {
  switch (true) {
    case document.isExpired:
      return 'Document has expired';
    case document.link === null:
      return 'Preview unavailable';
    default:
      return 'Unsupported file type';
  }
}

type Props = {
  document: DocumentInfo;
  labelSuffix: string;
};

const PreviewDocumentButton: React.FC<Props> = ({document, labelSuffix}) => {
  const isDisabled =
    document.link === null || document.isExpired || document.type === 'unknown';

  return (
    <ModalStateManager
      renderLauncher={({setOpen}) => {
        const button = (
          <Button
            kind="ghost"
            size="sm"
            hasIconOnly
            renderIcon={View}
            iconDescription="Preview"
            tooltipPosition="top"
            aria-label={`Preview document for ${labelSuffix}`}
            disabled={isDisabled}
            onClick={() => {
              tracking.track({
                eventName: 'document-previewed',
                documentType: document.type,
                contentType: document.contentType,
                size: document.size,
              });
              setOpen(true);
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
      }}
    >
      {({open, setOpen}) => (
        <DocumentPreviewModal
          open={open}
          setOpen={setOpen}
          document={document}
        />
      )}
    </ModalStateManager>
  );
};

export {PreviewDocumentButton};
