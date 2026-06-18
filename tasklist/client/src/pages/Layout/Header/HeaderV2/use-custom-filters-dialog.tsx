/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useLocation, useNavigate, useSearchParams} from 'react-router-dom';
import {CustomFiltersModal} from 'modules/tasks/available-tasks/CollapsiblePanel/CustomFiltersModal';
import {DeleteFilterModal} from 'modules/tasks/available-tasks/CollapsiblePanel/CustomFiltersModal/DeleteFilterModal';
import {getStateLocally} from 'modules/local-storage';

const BUILTIN_FILTERS = new Set([
  'all-open',
  'assigned-to-me',
  'unassigned',
  'completed',
]);

type CustomFilters = Record<string, {name?: string}>;

export type CustomFiltersDialog = {
  customFilters: CustomFilters;
  currentFilter: string;
  customFilterEntries: Array<[string, {name?: string}]>;
  openNewFilterModal: () => void;
  openEditFilterModal: (key: string) => void;
  openDeleteFilterModal: (key: string) => void;
  modals: React.ReactElement;
};

/**
 * Owns the custom-filters modal lifecycle (open/edit/delete state, navigation
 * side-effects, and the localStorage read trigger). `customFilters` reads
 * from `getStateLocally` on every render after a save/delete via an internal
 * bump counter — the modal mutates localStorage and we need the next render
 * to see the new entries.
 */
export function useCustomFiltersDialog(): CustomFiltersDialog {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const [bump, setBump] = useState(0);
  const [isNewFilterModalOpen, setIsNewFilterModalOpen] = useState(false);
  const [filterToEdit, setFilterToEdit] = useState<string>();
  const [filterToDelete, setFilterToDelete] = useState<string>();

  void bump;
  const customFilters = getStateLocally('customFilters') ?? {};
  const customFilterEntries = Object.entries(customFilters).filter(
    ([key]) => !BUILTIN_FILTERS.has(key),
  );

  const rawFilterParam = new URLSearchParams(location.search).get('filter');
  const currentFilter = rawFilterParam ?? 'all-open';

  const refreshLocalStorage = () => setBump((v) => v + 1);

  const navigateToFilter = (filter: string) => {
    const next = new URLSearchParams(searchParams);
    next.set('filter', filter);
    navigate({search: next.toString()});
  };

  const dismissEdit = () => {
    setIsNewFilterModalOpen(false);
    setFilterToEdit(undefined);
  };

  const modals = (
    <>
      <CustomFiltersModal
        filterId={filterToEdit}
        isOpen={isNewFilterModalOpen || filterToEdit !== undefined}
        onClose={dismissEdit}
        onSuccess={(filterId) => {
          dismissEdit();
          refreshLocalStorage();
          navigateToFilter(filterId);
        }}
        onDelete={() => {
          dismissEdit();
          refreshLocalStorage();
          navigateToFilter('all-open');
        }}
      />
      <DeleteFilterModal
        filterName={filterToDelete ?? ''}
        isOpen={filterToDelete !== undefined}
        onClose={() => setFilterToDelete(undefined)}
        onDelete={() => {
          refreshLocalStorage();
          if (currentFilter === filterToDelete) {
            navigateToFilter('all-open');
          }
          setFilterToDelete(undefined);
        }}
      />
    </>
  );

  return {
    customFilters,
    currentFilter,
    customFilterEntries,
    openNewFilterModal: () => setIsNewFilterModalOpen(true),
    openEditFilterModal: setFilterToEdit,
    openDeleteFilterModal: setFilterToDelete,
    modals,
  };
}
