/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ReactNode, useMemo, useState, useEffect} from 'react';
import {useLocation} from 'react-router-dom';
import {FilterableMultiSelect} from '@carbon/react';

import {useErrorHandling} from 'hooks';
import {Definition, getRandomId, getCollection} from 'services';
import {showError} from 'notifications';
import {t} from 'translation';
import {getMaxNumDataSourcesForReport} from 'config';

import {loadTenants} from './service';

interface MultiDefinitionSelectionProps {
  availableDefinitions: Definition[];
  changeDefinition: (key: string) => void;
  resetSelection: (selection: object) => void;
  selectedDefinitions: Definition[];
  onChange: (selectedDefinitions: Definition[]) => void;
  invalid?: boolean;
  invalidText?: ReactNode;
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
  invalid,
  invalidText,
}: MultiDefinitionSelectionProps): JSX.Element {
  const [reportDataSourceLimit, setReportDataSourceLimit] = useState(100);
  const location = useLocation();
  const {mightFail} = useErrorHandling();

  useEffect(() => {
    (async () => setReportDataSourceLimit(await getMaxNumDataSourcesForReport()))();
  }, []);

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
  disableIfLimitReached(allItems, selectedItems, reportDataSourceLimit);

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
        placeholder={t('common.select').toString()}
        titleText={t('common.definitionSelection.select.multiProcess')}
        size="sm"
        invalid={invalid}
        invalidText={invalidText}
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

function disableIfLimitReached(
  allItems: Item[],
  selectedItems: Item[],
  reportDataSourceLimit: number
) {
  // we use forEach because the multi-select expects that the reference to items does not change
  if (selectedItems.length >= reportDataSourceLimit) {
    allItems.forEach((item) => {
      if (!selectedItems.some((selectedItem) => selectedItem.id === item.id)) {
        item.disabled = true;
      }
    });
  } else {
    allItems.forEach((item) => (item.disabled = false));
  }
}
