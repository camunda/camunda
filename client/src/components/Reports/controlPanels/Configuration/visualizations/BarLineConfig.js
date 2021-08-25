/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {t} from 'translation';
import {Button, ButtonGroup} from 'components';

import BarChartConfig from './BarChartConfig';
import PointMarkersConfig from './subComponents/PointMarkersConfig';

import './BarLineConfig.scss';

export default function BarLineConfig({onChange, report}) {
  const configuration = report.data.configuration;

  const posibilities = {
    count: {
      bar: ['bar', 'line'],
      line: ['line', 'bar'],
    },
    duration: {
      bar: ['line', 'bar'],
      line: ['bar', 'line'],
    },
  };

  return (
    <>
      <fieldset className="visualizationSetting">
        <legend>{t('report.config.barLine.visualizationSettings')}</legend>
        {['count', 'duration'].map((measure, idx) => (
          <div className="measureContainer" key={idx}>
            <span>{t('report.view.' + measure)}</span>
            <ButtonGroup>
              <Button
                active={configuration.measureVisualizations[idx] === 'line'}
                onClick={() =>
                  onChange({measureVisualizations: {$set: posibilities[measure]['line']}})
                }
              >
                {t('report.config.barLine.line')}
              </Button>
              <Button
                active={configuration.measureVisualizations[idx] === 'bar'}
                onClick={() =>
                  onChange({measureVisualizations: {$set: posibilities[measure]['bar']}})
                }
              >
                {t('report.config.barLine.bar')}
              </Button>
            </ButtonGroup>
          </div>
        ))}
      </fieldset>
      <PointMarkersConfig {...{onChange, configuration}} />
      <BarChartConfig {...{onChange, report}} />
    </>
  );
}
