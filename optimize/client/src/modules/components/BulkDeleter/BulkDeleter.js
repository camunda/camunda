/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState} from 'react';
import {TableBatchAction} from '@carbon/react';
import {TrashCan} from '@carbon/icons-react';

import {Deleter} from 'components';
import {t} from 'translation';
import {useErrorHandling} from 'hooks';
import {showError} from 'notifications';

export default function BulkDeleter({
  deleteEntities,
  checkConflicts,
  conflictMessage,
  onDelete,
  selectedEntries,
  type = 'delete',
  ...rest
}) {
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
        entity={deleting && selectedEntries}
        deleteEntity={() =>
          mightFail(
            deleteEntities(selectedEntries),
            () => {
              reset();
              onDelete();
            },
            showError
          )
        }
        onClose={reset}
        deleteText={t('common.' + (type === 'remove' ? 'removeEntity' : 'deleteEntity'), {
          entity: 'items',
        })}
        deleteButtonText={t('common.' + type)}
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
