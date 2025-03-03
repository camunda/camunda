/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {FilterableMultiSelect} from '@carbon/react';
import {t} from 'translation';
import {Definition} from 'types';

import './FilterDefinitionSelection.scss';

export interface FilterDefinitionSelectionProps {
  availableDefinitions: Definition[];
  applyTo: Definition[];
  setApplyTo: (definitions: Definition[]) => void;
}

type Item = {
  id: string;
  label: string;
};

export default function FilterDefinitionSelection({
  availableDefinitions,
  applyTo,
  setApplyTo,
}: FilterDefinitionSelectionProps) {
  if (availableDefinitions.length <= 1) {
    return null;
  }

  const appliesToAll = applyTo.some(({identifier}) => identifier === 'all');
  const allItems = getItems(availableDefinitions);
  const selectedItems = appliesToAll ? getItems(availableDefinitions) : getItems(applyTo);

  const handleSelectionChange = (selectedItems: Item[]) => {
    const selectedDefinitions = selectedItems
      .map((item) => availableDefinitions.find((definition) => definition.identifier === item.id))
      .filter((definition): definition is Definition => definition !== undefined);

    setApplyTo(selectedDefinitions);
  };

  return (
    <div className="FilterDefinitionSelection">
      <FilterableMultiSelect
        id="filterDefintionSelection"
        initialSelectedItems={selectedItems}
        items={allItems}
        onChange={({selectedItems}) => handleSelectionChange(selectedItems)}
        titleText={t('common.definitionSelection.select.process')}
      />
    </div>
  );
}

function getItems(defintions: Definition[]): Item[] {
  return defintions.map((definition) => ({
    id: definition.identifier,
    label: definition.displayName?.toString() || definition.name || definition.key || '',
  }));
}
