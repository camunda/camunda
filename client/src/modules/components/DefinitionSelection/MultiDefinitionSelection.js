/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {withRouter} from 'react-router-dom';

import {MultiSelect} from 'components';
import {getRandomId, getCollection} from 'services';
import {showError} from 'notifications';
import {withErrorHandling} from 'HOC';
import {t} from 'translation';

import {loadTenants} from './service';

export function MultiDefinitionSelection({
  availableDefinitions,
  changeDefinition,
  resetSelection,
  selectedDefinitions,
  mightFail,
  onChange,
  location,
}) {
  function update(newDefinitions) {
    if (newDefinitions.length === 0) {
      resetSelection({});
      return onChange([]);
    }

    if (newDefinitions.length === 1) {
      return changeDefinition(newDefinitions[0].key);
    }

    resetSelection({versions: ['all']});
    mightFail(
      loadTenants(
        'process',
        newDefinitions.map(({key}) => ({
          key,
          versions: ['all'],
        })),
        getCollection(location.pathname)
      ),
      (tenantInfo) =>
        onChange(
          newDefinitions.map(({key, name}, idx) => ({
            key,
            name,
            versions: ['all'],
            tenantIds: tenantInfo[idx].tenants.map(({id}) => id),
            identifier: getRandomId(),
          }))
        ),
      showError
    );
  }

  return (
    <MultiSelect
      values={selectedDefinitions.map((definition) => ({
        value: definition.key,
        label: definition.name,
      }))}
      placeholder={t('common.select')}
      disabled={selectedDefinitions.length >= 10}
      onAdd={(value) => {
        update([...selectedDefinitions, availableDefinitions.find(({key}) => key === value)]);
      }}
      onRemove={(value) => {
        update(selectedDefinitions.filter(({key}) => key !== value));
      }}
      onClear={() => update([])}
    >
      {availableDefinitions
        .filter((def) => !selectedDefinitions.some(({key}) => key === def.key))
        .map(({name, key}) => {
          return (
            <MultiSelect.Option key={key} value={key} label={name || key}>
              {name || key}
            </MultiSelect.Option>
          );
        })}
    </MultiSelect>
  );
}

export default withRouter(withErrorHandling(MultiDefinitionSelection));
