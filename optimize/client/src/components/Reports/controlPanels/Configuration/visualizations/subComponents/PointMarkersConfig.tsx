/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {FormGroup, Toggle} from '@carbon/react';

import {t} from 'translation';

interface PointMarkersConfigProps {
  configuration: {pointMarkers?: boolean};
  onChange: (change: {pointMarkers: Record<string, boolean>}) => void;
}

export default function PointMarkersConfig({configuration, onChange}: PointMarkersConfigProps) {
  return (
    <FormGroup legendText={t('report.config.pointMarkers.legend')} className="PointMarkersConfig">
      <Toggle
        id="pointMarkersToggle"
        size="sm"
        toggled={configuration.pointMarkers}
        onToggle={(checked) => onChange({pointMarkers: {$set: checked}})}
        labelText={t('report.config.pointMarkers.enableMarkers').toString()}
        hideLabel
      />
    </FormGroup>
  );
}
