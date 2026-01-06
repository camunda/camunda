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
const IS_MULTI_INSTANCE_BODY = 'isMultiInstanceBody';
const IS_PLACEHOLDER = 'isPlaceholder';
const ANCHOR_ELEMENT_ID = 'anchorElementId';

type SelectionSearchParams = {
  [ELEMENT_ID]?: string;
  [ELEMENT_INSTANCE_KEY]?: string;
  [IS_MULTI_INSTANCE_BODY]?: boolean;
  [IS_PLACEHOLDER]?: boolean;
  [ANCHOR_ELEMENT_ID]?: string;
};

const deleteSelectionSearchParams = (params: URLSearchParams) => {
  params.delete(ELEMENT_ID);
  params.delete(ELEMENT_INSTANCE_KEY);
  params.delete(IS_MULTI_INSTANCE_BODY);
  params.delete(IS_PLACEHOLDER);
  params.delete(ANCHOR_ELEMENT_ID);
  return params;
};

const setSelectionSearchParams = (
  params: URLSearchParams,
  newParams: SelectionSearchParams,
) => {
  if (newParams[ELEMENT_ID]) {
    params.set(ELEMENT_ID, newParams[ELEMENT_ID]);
  }
  if (newParams[ELEMENT_INSTANCE_KEY]) {
    params.set(ELEMENT_INSTANCE_KEY, newParams[ELEMENT_INSTANCE_KEY]);
  }
  if (newParams[IS_MULTI_INSTANCE_BODY] === true) {
    params.set(IS_MULTI_INSTANCE_BODY, 'true');
  }
  if (newParams[IS_PLACEHOLDER] === true) {
    params.set(IS_PLACEHOLDER, 'true');
  }
  if (newParams[ANCHOR_ELEMENT_ID]) {
    params.set(ANCHOR_ELEMENT_ID, newParams[ANCHOR_ELEMENT_ID]);
  }
  return params;
};

const updateSelectionSearchParams = (
  params: URLSearchParams,
  newParams: SelectionSearchParams,
) => {
  return setSelectionSearchParams(
    deleteSelectionSearchParams(params),
    newParams,
  );
};

const useProcessInstanceElementSelection = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const {processInstanceId: processInstanceKey} =
    useProcessInstancePageParams();
  const elementInstanceKey = searchParams.get(ELEMENT_INSTANCE_KEY);

  const elementId = searchParams.get(ELEMENT_ID);
  const isMultiInstanceBody =
    searchParams.get(IS_MULTI_INSTANCE_BODY) === 'true';
  const isPlaceholder = searchParams.get(IS_PLACEHOLDER) === 'true';
  const anchorElementId = searchParams.get(ANCHOR_ELEMENT_ID);

  const selectElement = useCallback(
    ({
      elementId,
      isMultiInstanceBody = false,
    }: {
      elementId: string;
      isMultiInstanceBody?: boolean;
    }) => {
      setSearchParams((params) => {
        return updateSelectionSearchParams(params, {
          elementId,
          isMultiInstanceBody,
        });
      });
    },
    [setSearchParams],
  );

  const selectElementInstance = useCallback(
    ({
      elementId,
      elementInstanceKey,
      isMultiInstanceBody = false,
      isPlaceholder = false,
      anchorElementId,
    }: {
      elementId: string;
      elementInstanceKey: string;
      isMultiInstanceBody?: boolean;
      isPlaceholder?: boolean;
      anchorElementId?: string;
    }) => {
      setSearchParams((params) => {
        return updateSelectionSearchParams(params, {
          elementId,
          elementInstanceKey,
          isMultiInstanceBody,
          isPlaceholder,
          anchorElementId,
        });
      });
    },
    [setSearchParams],
  );

  const clearSelection = useCallback(() => {
    setSearchParams((params) => {
      return deleteSelectionSearchParams(params);
    });
  }, [setSearchParams]);

  // If elementInstanceKey is in URL, fetch element instance by key.
  // Skip fetching if elementInstanceKey is a placeholder key
  const {
    data: elementInstanceByKey,
    isFetching: isFetchingElementInstanceByKey,
  } = useElementInstance(elementInstanceKey ?? '', {
    enabled: !!elementInstanceKey && isPlaceholder === false,
  });

  // If only elementId is in URL, search for all instances
  const {data: searchResult, isFetching: isFetchingElementInstancesSearch} =
    useElementInstancesSearch({
      elementId: elementId ?? '',
      processInstanceKey: processInstanceKey ?? '',
      elementType: isMultiInstanceBody ? 'MULTI_INSTANCE_BODY' : undefined,
      enabled: !!elementId && !elementInstanceKey && !!processInstanceKey,
    });

  const resolvedElementInstance =
    elementInstanceByKey ??
    (searchResult?.page.totalItems === 1 ? searchResult?.items[0] : null);

  return {
    selectElement,
    selectElementInstance,
    clearSelection,
    /**
     * The resolved element instance based on the URL search params, is null
     * if no instance could be resolved or multiple instances exist for the given element id
     */
    resolvedElementInstance,
    /**
     * The currently selected element id from the URL search params
     */
    selectedElementId: elementId,
    /**
     * The currently selected element instance key from the URL search params
     */
    selectedElementInstanceKey: elementInstanceKey,
    isSelectedInstanceMultiInstanceBody: isMultiInstanceBody,
    isSelectedInstancePlaceholder: isPlaceholder,
    selectedAnchorElementId: anchorElementId,
    isFetchingElement:
      isFetchingElementInstanceByKey || isFetchingElementInstancesSearch,
  };
};

export {useProcessInstanceElementSelection};
