/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useSearchParams} from 'react-router-dom';
import {useCallback} from 'react';

const ELEMENT_ID = 'elementId';
const ELEMENT_INSTANCE_KEY = 'elementInstanceKey';

const useElementSelection = () => {
  const [searchParams, setSearchParams] = useSearchParams();

  const selectElement = useCallback(
    (elementId: string, elementInstanceKey?: string) => {
      setSearchParams({
        [ELEMENT_ID]: elementId,
        ...(elementInstanceKey
          ? {[ELEMENT_INSTANCE_KEY]: elementInstanceKey}
          : {}),
      });
    },

    [setSearchParams],
  );

  const clearSelection = useCallback(() => {
    searchParams.delete(ELEMENT_ID);
    searchParams.delete(ELEMENT_INSTANCE_KEY);

    setSearchParams(searchParams);
  }, [searchParams, setSearchParams]);

  return {
    selectElement,
    clearSelection,
    elementId: searchParams.get(ELEMENT_ID),
    elementInstanceKey: searchParams.get(ELEMENT_INSTANCE_KEY),
  };
};

export {useElementSelection, ELEMENT_ID, ELEMENT_INSTANCE_KEY};
