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

export function BulkMenu({bulkActions, selectedEntries, onChange, mightFail}) {
  const bulkRef = useRef();
  const selectionMode = selectedEntries.length > 0;
  const [action, setAction] = useState(null);
  const [conflict, setConflict] = useState(false);
  const deleteAction = bulkActions.find((action) => action.type === 'delete');

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

  function reset() {
    setConflict(false);
    setAction(null);
  }

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
          {deleteAction && (
            <Dropdown.Option onClick={() => setAction(deleteAction)}>
              {t('common.delete')}
            </Dropdown.Option>
          )}
        </Dropdown>
      )}

      <Deleter
        type="items"
        entity={action?.type === 'delete' && selectedEntries}
        deleteEntity={() =>
          mightFail(
            action.action(selectedEntries),
            () => {
              reset();
              onChange();
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
                {action.conflictMessage}
                <br /> <br />
              </>
            )}
            {t('common.deleter.areYouSureSelected')}
          </>
        }
        checkConflicts={action?.checkConflicts}
        onConflict={() => setConflict(true)}
      />
    </div>
  );
}

export default withErrorHandling(BulkMenu);
