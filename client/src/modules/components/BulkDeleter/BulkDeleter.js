/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
      <Dropdown.Option onClick={() => setDeleting(true)}>{t('common.delete')}</Dropdown.Option>
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
        deleteButtonText={t('common.delete')}
        descriptionText={
          <>
            {conflict && (
              <>
                {conflictMessage}
                <br /> <br />
              </>
            )}
            {t('common.deleter.areYouSureSelected')}
          </>
        }
        checkConflicts={checkConflicts}
        onConflict={() => setConflict(true)}
      />
    </>
  );
}

export default withErrorHandling(BulkDeleter);
