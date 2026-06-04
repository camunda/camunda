/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@carbon/react';
import {View} from '@carbon/react/icons';
import {ModalStateManager} from 'modules/components/ModalStateManager';
import type {DocumentInfo} from '../DocumentValueCell/parseDocumentVariable';
import {DocumentPreviewModal} from './DocumentPreviewModal';
import {tracking} from 'modules/tracking';

type Props = {
  document: DocumentInfo;
  variableName: string;
};

const PreviewDocumentButton: React.FC<Props> = ({document, variableName}) => {
  const tooltipText =
    document.type !== 'unknown'
      ? 'Preview'
      : 'Preview not available for this document type';

  return (
    <ModalStateManager
      renderLauncher={({setOpen}) => (
        <Button
          kind="ghost"
          size="sm"
          hasIconOnly
          renderIcon={View}
          iconDescription={tooltipText}
          tooltipPosition="top"
          // @ts-expect-error - Solves rendering issues in `DocumentListModal`. Not exposed through TS but used at runtime.
          autoAlign={true}
          aria-label={`Preview document for variable ${variableName}`}
          disabled={document.type === 'unknown'}
          onClick={() => {
            tracking.track({
              eventName: 'document-previewed',
              documentType: document.type,
              contentType: document.contentType ?? null,
              size: document.size ?? null,
            });
            setOpen(true);
          }}
        />
      )}
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
