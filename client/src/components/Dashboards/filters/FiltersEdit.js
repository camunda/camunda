/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {ActionItem, Dropdown} from 'components';
import {t} from 'translation';

import './FiltersEdit.scss';

export default function FiltersEdit({availableFilters, setAvailableFilters}) {
  function addFilter(type) {
    setAvailableFilters([...availableFilters, {type}]);
  }

  function hasFilter(type) {
    return availableFilters.some((filter) => filter.type === type);
  }

  function removeFilter(idxToRemove) {
    setAvailableFilters(availableFilters.filter((_, idx) => idx !== idxToRemove));
  }

  return (
    <div className="FiltersEdit">
      <h3>{t('dashboard.filter.label')}</h3>
      <span className="hint">{t('dashboard.filter.notice')}</span>
      <ul>
        {availableFilters.map(({type}, idx) => (
          <li key={type}>
            <ActionItem onClick={() => removeFilter(idx)}>
              {t('dashboard.filter.types.' + type)}
            </ActionItem>
          </li>
        ))}
        <li>
          <Dropdown label={t('common.filter.addFilter')}>
            {['startDate', 'endDate', 'state'].map((type) => (
              <Dropdown.Option
                key={type}
                disabled={hasFilter(type)}
                onClick={() => addFilter(type)}
              >
                {t('dashboard.filter.types.' + type)}
              </Dropdown.Option>
            ))}
          </Dropdown>
        </li>
      </ul>
    </div>
  );
}
