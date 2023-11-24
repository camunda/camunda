/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
        labelA={t('report.config.pointMarkers.enableMarkers').toString()}
        labelB={t('report.config.pointMarkers.enableMarkers').toString()}
      />
    </FormGroup>
  );
}
