/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useRef} from 'react';

import {Dropdown} from 'components';
import {t} from 'translation';

export default function BulkMenu({bulkActions, selectedEntries, onChange}) {
  const bulkRef = useRef();
  const selectionMode = selectedEntries.length > 0;

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
          {React.Children.map(bulkActions, (child, idx) =>
            React.cloneElement(child, {
              key: idx,
              onDelete: onChange,
              selectedEntries,
            })
          )}
        </Dropdown>
      )}
    </div>
  );
}
