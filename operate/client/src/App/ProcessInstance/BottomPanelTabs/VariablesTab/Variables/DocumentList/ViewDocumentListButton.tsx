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
import {DocumentListModal} from './DocumentListModal';

type Props = {
  documents: DocumentInfo[];
  isLowerBound: boolean;
  variableName: string;
};

const ViewDocumentListButton: React.FC<Props> = ({
  documents,
  isLowerBound,
  variableName,
}) => {
  return (
    <ModalStateManager
      renderLauncher={({setOpen}) => (
        <Button
          kind="ghost"
          size="sm"
          hasIconOnly
          renderIcon={View}
          iconDescription="View documents"
          tooltipPosition="top"
          tooltipAlignment="end"
          aria-label={`View documents for variable ${variableName}`}
          onClick={() => setOpen(true)}
        />
      )}
    >
      {({open, setOpen}) => (
        <DocumentListModal
          open={open}
          setOpen={setOpen}
          documents={documents}
          isLowerBound={isLowerBound}
          variableName={variableName}
        />
      )}
    </ModalStateManager>
  );
};

export {ViewDocumentListButton};
