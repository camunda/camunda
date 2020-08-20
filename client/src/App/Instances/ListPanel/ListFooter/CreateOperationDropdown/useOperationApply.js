/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import useFilterContext from 'modules/hooks/useFilterContext';
import {instanceSelection} from 'modules/stores/instanceSelection';
import useDataManager from 'modules/hooks/useDataManager';
import {useInstancesPollContext} from 'modules/contexts/InstancesPollContext';

export default function useOperationApply() {
  const {query} = useFilterContext();
  const {selectedInstanceIds, excludedInstanceIds, reset} = instanceSelection;
  const {applyBatchOperation} = useDataManager();
  const {addAllVisibleIds, addIds} = useInstancesPollContext();

  return {
    applyBatchOperation: (operationType) => {
      const filterIds = query.ids || [];

      // if ids are selected, ignore ids from filter
      // if no ids are selected, apply ids from filter
      const ids =
        selectedInstanceIds.length > 0 ? selectedInstanceIds : filterIds;

      if (selectedInstanceIds.length > 0) {
        addIds(selectedInstanceIds);
      } else {
        addAllVisibleIds(excludedInstanceIds);
      }

      applyBatchOperation(operationType, {
        ...query,
        ids,
        excludeIds: excludedInstanceIds,
      });

      reset();
    },
  };
}
