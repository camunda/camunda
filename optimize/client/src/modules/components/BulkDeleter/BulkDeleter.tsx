/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ComponentProps, useState} from 'react';
import {TableBatchAction} from '@carbon/react';
import {TrashCan} from '@carbon/icons-react';

import {Deleter} from 'components';
import {t} from 'translation';
import {useErrorHandling} from 'hooks';
import {EntityListEntity} from 'types';
import {showError} from 'notifications';

interface BulkDeleterProps extends Omit<ComponentProps<typeof TableBatchAction>, 'type'> {
  deleteEntities: (entries: EntityListEntity[]) => Promise<void>;
  checkConflicts?: (entity: EntityListEntity[]) => Promise<{conflictedItems: []}>;
  conflictMessage?: string;
  onDelete?: () => void;
  selectedEntries: EntityListEntity[];
  type?: 'delete' | 'remove';
}

export default function BulkDeleter({
  deleteEntities,
  checkConflicts,
  conflictMessage,
  onDelete,
  selectedEntries,
  type = 'delete',
  ...rest
}: BulkDeleterProps) {
  const [deleting, setDeleting] = useState(false);
  const [conflict, setConflict] = useState(false);
  const {mightFail} = useErrorHandling();

  function reset() {
    setConflict(false);
    setDeleting(false);
  }

  return (
    <>
      <TableBatchAction {...rest} onClick={() => setDeleting(true)} renderIcon={TrashCan}>
        {t('common.' + type)}
      </TableBatchAction>
      <Deleter
        type="items"
        entity={deleting ? selectedEntries : null}
        deleteEntity={() =>
          mightFail(
            deleteEntities(selectedEntries),
            () => {
              reset();
              onDelete?.();
            },
            showError
          )
        }
        onClose={reset}
        deleteText={t('common.' + (type === 'remove' ? 'removeEntity' : 'deleteEntity'), {
          entity: 'items',
        }).toString()}
        deleteButtonText={t('common.' + type).toString()}
        descriptionText={
          <>
            {conflict && (
              <>
                {conflictMessage}
                <br /> <br />
              </>
            )}
            {t('common.deleter.areYouSureSelected.' + type)}
          </>
        }
        checkConflicts={checkConflicts}
        onConflict={() => setConflict(true)}
      />
    </>
  );
}
