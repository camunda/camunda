/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect, useCallback} from 'react';

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
  const [processScope, setProcessScope] = useState(null);
  const {mightFail} = useErrorHandling();

  useEffect(() => {
    mightFail(loadAgenticDashboard(), setDashboard, showError);
  }, [mightFail]);

  const selectedPreset = DATE_PRESETS.find((p) => p.id === preset);
  const definitions = processScope ? [{key: processScope, versions: ['all']}] : [];
  const filter = [presetToFilter(selectedPreset)];

  const scopedEvaluateReport = useCallback(
    (id, tileFilter, query) => evaluateReport(id, tileFilter, query, definitions),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [processScope]
  );

  if (!dashboard) {
    return <Loading />;
  }

  const visibleTiles = dashboard.tiles?.filter((tile) => {
    if (processScope && tile.configuration?.visibleInL0Only) {
      return false;
    }
    if (!processScope && tile.configuration?.visibleInL1Only) {
      return false;
    }
    return true;
  });

  return (
    <div className="AgenticControlPlane">
      <div className="AgenticControlPlane__header">
        <h1 className="AgenticControlPlane__title">{t('agenticControlPlane.title')}</h1>
        <p className="AgenticControlPlane__description">{t('agenticControlPlane.description')}</p>
      </div>
      <FilterBar
        preset={preset}
        onPresetChange={setPreset}
        processScope={processScope}
        onProcessScopeChange={setProcessScope}
      />
      <DashboardRenderer
        key={processScope ?? '__all__'}
        loadTile={scopedEvaluateReport}
        tiles={visibleTiles}
        filter={filter}
      />
    </div>
  );
}
