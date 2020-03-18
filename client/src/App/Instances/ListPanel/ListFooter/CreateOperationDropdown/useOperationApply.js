/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import useFilterContext from 'modules/hooks/useFilterContext';
import useInstanceSelectionContext from 'modules/hooks/useInstanceSelectionContext';
import useDataManager from 'modules/hooks/useDataManager';
import {useInstancesPollContext} from 'modules/contexts/InstancesPollContext';

export default function useOperationApply() {
  const {query} = useFilterContext();
  const {ids: selectedIds, excludeIds, reset} = useInstanceSelectionContext();
  const {applyBatchOperation} = useDataManager();
  const {addAllVisibleIds, addIds} = useInstancesPollContext();

  return {
    applyBatchOperation: operationType => {
      reset();

      const filterIds = query.ids || [];

      // if ids are selected, ignore ids from filter
      // if no ids are selected, apply ids from filter
      const ids = selectedIds.length > 0 ? selectedIds : filterIds;

      if (selectedIds.length > 0) {
        addIds(selectedIds);
      } else {
        addAllVisibleIds(excludeIds);
      }

      applyBatchOperation(operationType, {
        ...query,
        ids,
        excludeIds
      });
    }
  };
}
