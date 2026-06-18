/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getStateLocally} from 'modules/local-storage';
import type {NamedCustomFilters} from 'modules/tasks/filters/customFiltersSchema';
import {createContext, useCallback, useContext, useState} from 'react';
import {CustomFiltersModal} from '.';
import {DeleteFilterModal} from './DeleteFilterModal';
import {useSearchParams} from 'react-router-dom';
import {getNavLinkSearchParam} from 'modules/features/tasks/filters/getNavLinkSearchParam';
import {useCurrentUser} from 'modules/api/useCurrentUser.query';

const EDITING_PREFIX = 'editing_';
const DELETING_PREFIX = 'deleting_';

type CustomFiltersContextValue = {
  customFilters: Record<string, NamedCustomFilters>;
  status:
    | 'initial'
    | 'adding'
    | `${typeof EDITING_PREFIX}${string}`
    | `${typeof DELETING_PREFIX}${string}`;
  startEditing: (key: string) => void;
  startDeleting: (key: string) => void;
  startAdding: () => void;
  reset: () => void;
};

const CustomFiltersContext = createContext<CustomFiltersContextValue | null>(
  null,
);

function useCustomFiltersContext() {
  const context = useContext(CustomFiltersContext);

  if (context === null) {
    throw new Error(
      'useCustomFiltersContext must be used within a CustomFiltersProvider',
    );
  }

  return context;
}

type Props = {
  children: React.ReactNode;
};

const CustomFiltersProvider: React.FC<Props> = ({children}) => {
  const [_, setSearchParams] = useSearchParams();
  const [customFilters, setCustomFilters] = useState(
    getStateLocally('customFilters') ?? {},
  );
  const {data: currentUser} = useCurrentUser();
  const username = currentUser?.username ?? '';
  const [status, setStatus] =
    useState<CustomFiltersContextValue['status']>('initial');
  const filterToEdit = status.startsWith(EDITING_PREFIX)
    ? status.replace(EDITING_PREFIX, '')
    : undefined;
  const filterToDelete = status.startsWith(DELETING_PREFIX)
    ? status.replace(DELETING_PREFIX, '')
    : undefined;

  const startEditing = useCallback((key: string) => {
    setStatus(`${EDITING_PREFIX}${key}`);
  }, []);

  const startDeleting = useCallback((key: string) => {
    setStatus(`${DELETING_PREFIX}${key}`);
  }, []);

  const startAdding = useCallback(() => {
    setStatus('adding');
  }, []);

  const reset = useCallback(() => {
    setStatus('initial');
  }, []);

  const updateCustomFilters = useCallback(() => {
    setCustomFilters(getStateLocally('customFilters') ?? {});
    setStatus('initial');
  }, []);

  const handleDelete = useCallback(
    (filterName: string) => {
      if (filterName === filterToDelete) {
        setSearchParams(
          getNavLinkSearchParam({
            currentParams: new URLSearchParams(),
            username,
            filter: 'all-open',
          }),
        );
      }
      updateCustomFilters();
    },
    [filterToDelete, updateCustomFilters, setSearchParams, username],
  );

  return (
    <CustomFiltersContext.Provider
      value={{
        customFilters,
        status,
        startEditing,
        startDeleting,
        startAdding,
        reset,
      }}
    >
      {children}
      <CustomFiltersModal
        filterId={filterToEdit}
        isOpen={status.startsWith(EDITING_PREFIX) || status === 'adding'}
        onClose={reset}
        onEditSuccess={(filterName) => {
          updateCustomFilters();

          setSearchParams(
            getNavLinkSearchParam({
              currentParams: new URLSearchParams(),
              username,
              filter: filterName,
            }),
          );
        }}
        onSuccess={(filterName) => {
          updateCustomFilters();

          if (status === 'adding') {
            setSearchParams(
              getNavLinkSearchParam({
                currentParams: new URLSearchParams(),
                username,
                filter: filterName,
              }),
            );
          }
        }}
        onDelete={handleDelete}
      />
      <DeleteFilterModal
        filterName={filterToDelete ?? ''}
        isOpen={status.startsWith(DELETING_PREFIX)}
        onClose={reset}
        onDelete={handleDelete}
      />
    </CustomFiltersContext.Provider>
  );
};

// eslint-disable-next-line react-refresh/only-export-components
export {CustomFiltersProvider, useCustomFiltersContext};
