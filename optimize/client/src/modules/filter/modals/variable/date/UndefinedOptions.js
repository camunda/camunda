/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
