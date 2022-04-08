/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {t} from 'translation';
import {Button, ButtonGroup} from 'components';

import BarChartConfig from './BarChartConfig';
import PointMarkersConfig from './subComponents/PointMarkersConfig';

import './BarLineConfig.scss';

export default function BarLineConfig({onChange, report}) {
  const configuration = report.data.configuration;

  const otherMeasure = {
    frequency: 'duration',
    duration: 'frequency',
  };

  return (
    <div className="BarLineConfig">
      <fieldset>
        <legend>{t('report.config.barLine.visualizationSettings')}</legend>
        {['frequency', 'duration'].map((measure, idx) => (
          <div className="measureContainer" key={idx}>
            <span>{t('report.view.' + (measure === 'frequency' ? 'count' : 'duration'))}</span>
            <ButtonGroup>
              <Button
                active={configuration.measureVisualizations[measure] === 'line'}
                onClick={() =>
                  onChange({
                    measureVisualizations: {
                      $set: {[measure]: 'line', [otherMeasure[measure]]: 'bar'},
                    },
                  })
                }
              >
                {t('report.config.barLine.line')}
              </Button>
              <Button
                active={configuration.measureVisualizations[measure] === 'bar'}
                onClick={() =>
                  onChange({
                    measureVisualizations: {
                      $set: {[measure]: 'bar', [otherMeasure[measure]]: 'line'},
                    },
                  })
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
    </div>
  );
}
