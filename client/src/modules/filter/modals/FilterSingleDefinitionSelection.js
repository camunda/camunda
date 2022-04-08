/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {Labeled, Typeahead} from 'components';
import {t} from 'translation';

import './FilterSingleDefinitionSelection.scss';

export default function FilterSingleDefinitionSelection({
  availableDefinitions,
  applyTo,
  setApplyTo,
}) {
  if (availableDefinitions.length <= 1) {
    return null;
  }

  return (
    <div className="FilterSingleDefinitionSelection">
      <Labeled label={t('common.definitionSelection.select.process')}>
        <Typeahead
          initialValue={applyTo}
          placeholder={t('dashboard.addButton.selectReportPlaceholder')}
          onChange={setApplyTo}
        >
          {availableDefinitions
            .filter((definition) => definition.versions.length && definition.tenantIds.length)
            .map((definition) => (
              <Typeahead.Option key={definition.identifier} value={definition}>
                {definition.displayName || definition.name || definition.key}
              </Typeahead.Option>
            ))}
        </Typeahead>
      </Labeled>
    </div>
  );
}
