/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect, useRef} from 'react';

import {Dropdown, Deleter} from 'components';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import {deleteEntities, checkConflicts} from './service';

export function BulkMenu({selectedEntries, reload, mightFail, setSelected}) {
  const bulkRef = useRef();
  const selectionMode = selectedEntries.length > 0;
  const [deleting, setDeleting] = useState(false);
  const [conflictType, setConflictType] = useState();

  // remove the primary state of the main button when bulk actions button exists
  useEffect(() => {
    const button = bulkRef.current?.parentNode.querySelector('.action .Button.main');
    const togglePrimary = () => button?.classList.toggle('primary', !selectionMode);
    if (button) {
      togglePrimary();
      const menuObserver = new MutationObserver(togglePrimary);
      menuObserver.observe(button, {attributes: true});
      return () => {
        menuObserver.disconnect();
      };
    }
  }, [selectionMode]);

  return (
    <div ref={bulkRef} className="BulkMenu">
      {selectionMode && (
        <Dropdown
          main
          primary
          label={`${selectedEntries.length} ${t(
            'common.itemSelected.' + (selectedEntries.length > 1 ? 'label-plural' : 'label')
          )}`}
        >
          <Dropdown.Option
            onClick={() => {
              setDeleting(true);
            }}
          >
            {t('common.delete')}
          </Dropdown.Option>
        </Dropdown>
      )}

      <Deleter
        type="items"
        entity={deleting}
        deleteEntity={() =>
          mightFail(
            deleteEntities(selectedEntries),
            () => {
              setSelected([]);
              setConflictType();
              reload();
            },
            showError
          )
        }
        onClose={() => {
          setDeleting(false);
          setConflictType();
        }}
        deleteButtonText={t('common.delete')}
        descriptionText={
          <>
            {conflictType && (
              <>
                {t(`common.deleter.affectedMessage.bulk.${conflictType}`)}
                <br /> <br />
              </>
            )}
            {t('common.deleter.areYouSureSelected')}
          </>
        }
        checkConflicts={() => checkConflicts(selectedEntries)}
        onConflict={(conflictedItems) => {
          const hasReport = conflictedItems.some((item) => item.type === 'report');
          setConflictType(hasReport ? 'process' : 'report');
        }}
      />
    </div>
  );
}

export default withErrorHandling(BulkMenu);
