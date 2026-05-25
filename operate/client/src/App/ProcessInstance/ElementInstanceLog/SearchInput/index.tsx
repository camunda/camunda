/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {useEffect, useRef} from 'react';
import {Search} from '@carbon/react';
import {elementInstanceHistorySearchStore} from 'modules/stores/elementInstanceHistorySearch';

const MAX_LENGTH = 200;

const SearchInput: React.FC = observer(() => {
  const containerRef = useRef<HTMLDivElement>(null);

  // CMD+F / CTRL+F focuses and selects all text in the search input,
  // matching the keyboard shortcut Modeler uses for its element search.
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'f') {
        e.preventDefault();
        const input =
          containerRef.current?.querySelector<HTMLInputElement>('input');
        input?.focus();
        input?.select();
      }
    };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, []);

  return (
    <div ref={containerRef}>
      <Search
        size="sm"
        labelText="Search element instances by name or ID"
        placeholder="Search by name or ID"
        data-testid="instance-history-search-input"
        value={elementInstanceHistorySearchStore.state.searchText}
        maxLength={MAX_LENGTH}
        onChange={(event) => {
          // maxLength on the input prevents typing/paste beyond the limit at
          // the DOM level; the slice is defense-in-depth for edge cases where
          // a programmatic value could exceed it.
          elementInstanceHistorySearchStore.setSearchText(
            event.target.value.slice(0, MAX_LENGTH),
          );
        }}
        onClear={() => {
          elementInstanceHistorySearchStore.setSearchText('');
        }}
      />
    </div>
  );
});

export {SearchInput};
