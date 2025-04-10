/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Modal} from 'common/components/Modal';
import {getStateLocally, storeStateLocally} from 'common/local-storage';
import {useTranslation, Trans} from 'react-i18next';

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
      modalHeading={
        isOpen ? t('customFiltersModalDeleteModalHeading') : undefined
      }
      primaryButtonText={t('customFiltersModalConfirmDeletionButton')}
      secondaryButtonText={t('tasksFiltersModalCancelButtonLabel')}
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
    >
      <p>
        <Trans
          i18nKey="customFiltersModalDeleteModalBody"
          values={{
            name: getStateLocally('customFilters')?.[filterName]?.name,
          }}
          components={{
            strong: <strong />,
          }}
        ></Trans>
      </p>
    </Modal>
  );
};

export {DeleteFilterModal};
