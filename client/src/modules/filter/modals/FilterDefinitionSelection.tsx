/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Labeled, MultiSelect} from 'components';
import {t} from 'translation';
import {Definition} from 'types';

import './FilterDefinitionSelection.scss';

export interface FilterDefinitionSelectionProps {
  availableDefinitions: Definition[];
  applyTo: Definition[];
  setApplyTo: (definitions: Definition[]) => void;
}

export default function FilterDefinitionSelection({
  availableDefinitions,
  applyTo,
  setApplyTo,
}: FilterDefinitionSelectionProps) {
  function onAdd(definition: Definition) {
    if (definition.identifier === 'all') {
      setApplyTo([definition]);
    } else {
      setApplyTo([...applyTo, definition]);
    }
  }

  function onClear() {
    setApplyTo([]);
  }

  function onRemove(definition: Definition | undefined) {
    setApplyTo(applyTo.filter((applied) => applied.identifier !== definition?.identifier));
  }

  if (availableDefinitions.length <= 1) {
    return null;
  }

  const appliesToAll = applyTo.some(({identifier}) => identifier === 'all');
  const options = [];
  if (!appliesToAll) {
    options.push(
      <MultiSelect.Option
        id="all"
        key="all"
        value={{
          identifier: 'all',
          displayName: t('common.filter.definitionSelection.allProcesses'),
        }}
      >
        {t('common.filter.definitionSelection.allProcesses')}
      </MultiSelect.Option>,
      ...availableDefinitions
        .filter((definition) =>
          applyTo.every((alreadyAdded) => alreadyAdded.identifier !== definition.identifier)
        )
        .map((definition) => (
          <MultiSelect.Option
            key={definition.identifier}
            id={definition.identifier}
            value={definition}
          >
            {definition.displayName || definition.name || definition.key}
          </MultiSelect.Option>
        ))
    );
  }

  return (
    <div className="FilterDefinitionSelection">
      <Labeled label={t('common.definitionSelection.select.process')}>
        <MultiSelect
          onAdd={onAdd}
          onClear={onClear}
          onRemove={onRemove}
          values={applyTo.map((definition) => ({
            value: definition,
            label: definition.displayName?.toString() || definition.name || definition.key || '',
          }))}
          persistMenu={false}
        >
          {options}
        </MultiSelect>
      </Labeled>
    </div>
  );
}
