/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
      labelText={t('report.config.showGradientBars').toString()}
      hideLabel
    />
  );
}
