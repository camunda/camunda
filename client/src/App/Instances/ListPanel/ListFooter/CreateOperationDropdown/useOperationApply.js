/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import useFilterContext from 'modules/hooks/useFilterContext';
import useInstanceSelectionContext from 'modules/hooks/useInstanceSelectionContext';
import useDataManager from 'modules/hooks/useDataManager';

export default function useOperationApply() {
  const {query} = useFilterContext();
  const {ids, excludeIds, reset} = useInstanceSelectionContext();
  const {applyBatchOperation} = useDataManager();

  return {
    applyOperation: operationType => {
      reset();
      applyBatchOperation(operationType, {...query, ids, excludeIds});
    }
  };
}
