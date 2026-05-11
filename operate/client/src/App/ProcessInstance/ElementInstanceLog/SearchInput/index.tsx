/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {useEffect, useState} from 'react';
import {Search} from '@carbon/react';
import {elementInstanceHistorySearchStore} from 'modules/stores/elementInstanceHistorySearch';

const MAX_LENGTH = 200;

const SearchInput: React.FC = observer(() => {
  const [value, setValue] = useState<string>(
    elementInstanceHistorySearchStore.state.searchText,
  );

  // Keep local state in sync if the store is reset externally (e.g. on
  // modification-mode toggle or process-instance change).
  useEffect(() => {
    if (elementInstanceHistorySearchStore.state.searchText !== value) {
      setValue(elementInstanceHistorySearchStore.state.searchText);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [elementInstanceHistorySearchStore.state.searchText]);

  return (
    <Search
      size="sm"
      labelText="Search element instances by name or ID"
      placeholder="Search by name or ID"
      data-testid="instance-history-search-input"
      value={value}
      onChange={(event) => {
        const next = event.target.value.slice(0, MAX_LENGTH);
        setValue(next);
        elementInstanceHistorySearchStore.setSearchText(next);
      }}
      onClear={() => {
        setValue('');
        elementInstanceHistorySearchStore.setSearchText('');
      }}
    />
  );
});

export {SearchInput};
