/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Toggle} from '@carbon/react';

import {t} from 'translation';

interface GradientBarsSwitchProps {
  configuration: {showGradientBars?: boolean};
  onChange: (change: {showGradientBars: Record<string, boolean>}) => void;
}

export default function GradientBarsSwitch({configuration, onChange}: GradientBarsSwitchProps) {
  return (
    <Toggle
      size="sm"
      id="showGradientBarsToggle"
      toggled={!!configuration.showGradientBars}
      onToggle={(checked) => onChange({showGradientBars: {$set: checked}})}
      labelA={t('report.config.showGradientBars').toString()}
      labelB={t('report.config.showGradientBars').toString()}
    />
  );
}
