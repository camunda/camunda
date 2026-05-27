/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect} from 'react';

import {useErrorHandling} from 'hooks';
import {showError} from 'notifications';
import {DashboardRenderer, Loading} from 'components';
import {evaluateReport} from 'services';
import {t} from 'translation';

import {DATE_PRESETS, FilterBar} from './FilterBar';
import {loadAgenticDashboard} from './service';

import './AgenticControlPlane.scss';

function presetToFilter(preset) {
  return {
    type: 'instanceEndDate',
    filterLevel: 'instance',
    data: {
      type: 'rolling',
      start: {value: preset.value, unit: preset.unit},
      end: null,
      excludeUndefined: false,
      includeUndefined: false,
    },
  };
}

export function AgenticControlPlane() {
  const [preset, setPreset] = useState('30d');
  const [dashboard, setDashboard] = useState(null);
  const {mightFail} = useErrorHandling();

  useEffect(() => {
    mightFail(loadAgenticDashboard(), setDashboard, showError);
  }, [mightFail]);

  const selectedPreset = DATE_PRESETS.find((p) => p.id === preset);
  const filter = [presetToFilter(selectedPreset)];

  if (!dashboard) {
    return <Loading />;
  }

  return (
    <div className="AgenticControlPlane">
      <div className="AgenticControlPlane__header">
        <h1 className="AgenticControlPlane__title">
          {t('agenticControlPlane.title') || 'Agentic control plane'}
        </h1>
        <p className="AgenticControlPlane__description">
          {t('agenticControlPlane.description') ||
            'Monitor AI agent adoption, token spend, behavior, and reliability across your processes.'}
        </p>
      </div>
      <FilterBar preset={preset} onPresetChange={setPreset} />
      <DashboardRenderer loadTile={evaluateReport} tiles={dashboard.tiles} filter={filter} />
    </div>
  );
}
