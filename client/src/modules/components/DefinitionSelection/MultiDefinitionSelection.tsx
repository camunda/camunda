/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useMemo} from 'react';
import {useLocation} from 'react-router-dom';
import {FilterableMultiSelect} from '@carbon/react';

import {useErrorHandling} from 'hooks';
import {Definition, getRandomId, getCollection} from 'services';
import {showError} from 'notifications';
import {t} from 'translation';

import {loadTenants} from './service';

interface MultiDefinitionSelectionProps {
  availableDefinitions: Definition[];
  changeDefinition: (key: string) => void;
  resetSelection: (selection: {}) => void;
  selectedDefinitions: Definition[];
  onChange: (selectedDefinitions: Definition[]) => void;
}

type Item = {
  id: string;
  label: string;
  disabled?: boolean;
};

function MultiDefinitionSelection({
  availableDefinitions,
  changeDefinition,
  resetSelection,
  selectedDefinitions,
  onChange,
}: MultiDefinitionSelectionProps): JSX.Element {
  const location = useLocation();
  const {mightFail} = useErrorHandling();

  function update(newDefinitions: Definition[]) {
    if (newDefinitions.length === 0) {
      resetSelection({});
      return onChange([]);
    }

    if (newDefinitions.length === 1 && newDefinitions[0]) {
      return changeDefinition(newDefinitions[0].key);
    }

    resetSelection({versions: ['all']});
    mightFail(
      loadTenants(
        'process',
        newDefinitions.map(({key}) => ({
          key: key,
          versions: ['all'],
        })),
        getCollection(location.pathname)
      ),
      (tenantInfo: Awaited<ReturnType<typeof loadTenants>>) =>
        onChange(
          newDefinitions.map(({key, name}, idx) => ({
            key,
            name,
            versions: ['all'],
            tenantIds: tenantInfo[idx]?.tenants.map(({id}) => id),
            identifier: getRandomId(),
          }))
        ),
      showError
    );
  }

  const allItems = useMemo(() => getItems(availableDefinitions), [availableDefinitions]);
  const selectedItems = allItems.filter((item) =>
    selectedDefinitions.some((def) => def.key === item.id)
  );
  disableIfLimitReached(allItems, selectedItems);

  const handleSelectionChange = (selectedItems: Item[]) => {
    const selectedKeys = selectedItems.map((item) => item.id);
    const selectedDefintions = availableDefinitions.filter(({key}) => selectedKeys.includes(key));
    update(selectedDefintions);
  };

  return (
    <div className="entry">
      <FilterableMultiSelect
        id="multiDefinitionSelection"
        initialSelectedItems={selectedItems}
        items={allItems}
        onChange={({selectedItems}) => handleSelectionChange(selectedItems)}
        placeholder={t('common.select')}
        titleText={t('common.definitionSelection.select.multiProcess')}
        size="sm"
      />
    </div>
  );
}

export default MultiDefinitionSelection;

function getItems(defintions: Definition[]): Item[] {
  return defintions.map(({name, key}) => ({
    id: key,
    label: name || key,
  }));
}

function disableIfLimitReached(allItems: Item[], selectedItems: Item[]) {
  // we use forEach because the multi-select expects that the reference to items does not change
  if (selectedItems.length >= 10) {
    allItems.forEach((item) => {
      if (!selectedItems.some((selectedItem) => selectedItem.id === item.id)) {
        item.disabled = true;
      }
    });
  } else {
    allItems.forEach((item) => (item.disabled = false));
  }
}
