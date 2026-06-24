/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Stack, Toggle} from '@carbon/react';

import {t} from 'translation';

interface RelativeAbsoluteSelectionProps {
  hideRelative?: boolean;
  absolute: boolean;
  relative: boolean;
  onChange: (type: string, value: boolean) => void;
}

export default function RelativeAbsoluteSelection({
  hideRelative,
  absolute,
  relative,
  onChange,
}: RelativeAbsoluteSelectionProps) {
  return (
    <Stack gap={4}>
      <Toggle
        id="showAbsoluteValueToggle"
        size="sm"
        toggled={absolute}
        onToggle={(checked) => onChange('absolute', checked)}
        labelText={t('report.config.tooltips.showAbsolute').toString()}
        hideLabel
      />

      {!hideRelative && (
        <Toggle
          id="showRelativeValueToggle"
          size="sm"
          toggled={relative}
          onToggle={(checked) => onChange('relative', checked)}
          labelText={t('report.config.tooltips.showRelative.process').toString()}
          hideLabel
        />
      )}
    </Stack>
  );
}
