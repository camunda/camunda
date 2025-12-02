/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useSearchParams} from 'react-router-dom';
import {useCallback} from 'react';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {useElementInstance} from 'modules/queries/elementInstances/useElementInstance';
import {useElementInstancesSearch} from 'modules/queries/elementInstances/useElementInstancesSearch';

const ELEMENT_ID = 'elementId';
const ELEMENT_INSTANCE_KEY = 'elementInstanceKey';

const useProcessInstanceElementSelection = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const {processInstanceId: processInstanceKey} =
    useProcessInstancePageParams();
  const elementInstanceKey = searchParams.get(ELEMENT_INSTANCE_KEY);
  const elementId = searchParams.get(ELEMENT_ID);

  const selectElement = useCallback(
    (elementId: string) => {
      setSearchParams({
        [ELEMENT_ID]: elementId,
      });
    },
    [setSearchParams],
  );

  const selectElementInstance = useCallback(
    (elementId: string, elementInstanceKey: string) => {
      setSearchParams({
        [ELEMENT_ID]: elementId,
        [ELEMENT_INSTANCE_KEY]: elementInstanceKey,
      });
    },
    [setSearchParams],
  );

  const clearSelection = useCallback(() => {
    setSearchParams((prevParams) => {
      const newParams = new URLSearchParams(prevParams);
      newParams.delete(ELEMENT_ID);
      newParams.delete(ELEMENT_INSTANCE_KEY);
      return newParams;
    });
  }, [setSearchParams]);

  // If elementInstanceKey is in URL, fetch element instance by key
  const {
    data: elementInstanceByKey,
    isFetching: isFetchingElementInstanceByKey,
  } = useElementInstance(elementInstanceKey ?? '', {
    enabled: !!elementInstanceKey,
  });

  // If only elementId is in URL, search for all instances
  const {data: searchResult, isFetching: isFetchingElementInstancesSearch} =
    useElementInstancesSearch(
      elementId ?? '',
      processInstanceKey ?? '',
      undefined,
      {
        enabled: !!elementId && !elementInstanceKey && !!processInstanceKey,
      },
    );

  const selectedElementInstance =
    elementInstanceByKey ??
    (searchResult?.page.totalItems === 1 ? searchResult?.items[0] : null);

  return {
    selectElement,
    selectElementInstance,
    clearSelection,
    selectedElementInstance,
    selectedElementId: elementId,
    isFetchingElement:
      isFetchingElementInstanceByKey || isFetchingElementInstancesSearch,
  };
};

export {useProcessInstanceElementSelection};
