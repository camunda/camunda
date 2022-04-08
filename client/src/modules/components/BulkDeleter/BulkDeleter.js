/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState} from 'react';

import {Dropdown, Deleter} from 'components';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

export function BulkDeleter({
  deleteEntities,
  checkConflicts,
  conflictMessage,
  onDelete,
  selectedEntries,
  type = 'delete',
  mightFail,
}) {
  const [deleting, setDeleting] = useState(false);
  const [conflict, setConflict] = useState(false);

  function reset() {
    setConflict(false);
    setDeleting(false);
  }

  return (
    <>
      <Dropdown.Option onClick={() => setDeleting(true)}>{t('common.' + type)}</Dropdown.Option>
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

export default withErrorHandling(BulkDeleter);
