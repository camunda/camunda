/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {FormGroup, Toggle} from '@carbon/react';

import {t} from 'translation';

export default function UndefinedOptions({
  includeUndefined,
  changeIncludeUndefined,
  excludeUndefined,
  changeExcludeUndefined,
}) {
  return (
    <FormGroup legendText={t('common.filter.variableModal.undefinedValuesLabel')}>
      <Toggle
        id="includeUndefined"
        toggled={includeUndefined}
        onToggle={(checked) => changeIncludeUndefined(checked)}
        labelText={t('common.filter.variableModal.includeUndefined')}
        hideLabel
        size="sm"
      />
      <Toggle
        id="excludeUndefined"
        toggled={excludeUndefined}
        onToggle={(checked) => changeExcludeUndefined(checked)}
        labelText={t('common.filter.variableModal.excludeUndefined')}
        hideLabel
        size="sm"
      />
    </FormGroup>
  );
}
