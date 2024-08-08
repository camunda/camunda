/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {FormGroup, RadioButton, RadioButtonGroup, Stack} from '@carbon/react';

import {t} from 'translation';

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
    <Stack gap={4} className="BarLineConfig">
      <FormGroup legendText={t('report.config.barLine.visualizationSettings')}>
        <Stack gap={4}>
          {['frequency', 'duration'].map((measure) => (
            <RadioButtonGroup
              key={`${measure}=${configuration.measureVisualizations[measure]}`}
              name={measure}
              legendText={t('report.view.' + (measure === 'frequency' ? 'count' : 'duration'))}
            >
              <RadioButton
                value="line"
                name={`${measure}-line`}
                labelText={t('report.config.barLine.line')}
                checked={configuration.measureVisualizations[measure] === 'line'}
                onClick={() =>
                  onChange({
                    measureVisualizations: {
                      $set: {[measure]: 'line', [otherMeasure[measure]]: 'bar'},
                    },
                  })
                }
              />
              <RadioButton
                value="bar"
                name={`${measure}-bar`}
                labelText={t('report.config.barLine.bar')}
                checked={configuration.measureVisualizations[measure] === 'bar'}
                onClick={() =>
                  onChange({
                    measureVisualizations: {
                      $set: {[measure]: 'bar', [otherMeasure[measure]]: 'line'},
                    },
                  })
                }
              />
            </RadioButtonGroup>
          ))}
        </Stack>
      </FormGroup>
      <PointMarkersConfig {...{onChange, configuration}} />
      <BarChartConfig {...{onChange, report}} />
    </Stack>
  );
}
