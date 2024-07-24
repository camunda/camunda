/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Modal} from 'modules/components/Modal';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';
import { useTranslation } from 'react-i18next';

type Props = {
  isOpen: boolean;
  filterName: string;
  onClose: () => void;
  onDelete: () => void;
  'data-testid'?: string;
};

const DeleteFilterModal: React.FC<Props> = ({
  isOpen,
  onClose,
  onDelete,
  filterName,
  ...props
}) => {
  
  const {t} = useTranslation();

  return (
    <Modal
      {...props}
      danger
      open={isOpen}
      size="sm"
      modalLabel={isOpen ? t('deleteFilterModalLabel') : undefined}
      modalHeading={isOpen ? t('deleteFilterModalHeading') : undefined}
      primaryButtonText={t('confirmDeletionButtonText')}
      secondaryButtonText={t('cancelButtonText')}
      onRequestClose={onClose}
      onRequestSubmit={() => {
        const customFilters = Object.entries(
          getStateLocally('customFilters') ?? {},
        );
        storeStateLocally(
          'customFilters',
          Object.fromEntries(
            customFilters.filter(([name]) => filterName !== name),
          ),
        );

        onDelete();
      }}
    />
  );
};

export {DeleteFilterModal};
