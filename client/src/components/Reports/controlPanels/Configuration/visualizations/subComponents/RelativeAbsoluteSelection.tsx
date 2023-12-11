/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Stack, Toggle} from '@carbon/react';

import {t} from 'translation';

interface RelativeAbsoluteSelectionProps {
  hideRelative?: boolean;
  absolute: boolean;
  relative: boolean;
  reportType: string;
  onChange: (type: string, value: boolean) => void;
}

export default function RelativeAbsoluteSelection({
  hideRelative,
  absolute,
  relative,
  reportType,
  onChange,
}: RelativeAbsoluteSelectionProps) {
  return (
    <Stack gap={4}>
      <Toggle
        id="showAbsoluteValueToggle"
        size="sm"
        toggled={absolute}
        onToggle={(checked) => onChange('absolute', checked)}
        labelA={t('report.config.tooltips.showAbsolute').toString()}
        labelB={t('report.config.tooltips.showAbsolute').toString()}
      />

      {!hideRelative && (
        <Toggle
          id="showRelativeValueToggle"
          size="sm"
          toggled={relative}
          onToggle={(checked) => onChange('relative', checked)}
          labelA={t('report.config.tooltips.showRelative.' + reportType).toString()}
          labelB={t('report.config.tooltips.showRelative.' + reportType).toString()}
        />
      )}
    </Stack>
  );
}
