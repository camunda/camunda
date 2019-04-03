/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {ColorPicker, Switch, LabeledInput} from 'components';
import ChartTargetInput from './subComponents/ChartTargetInput';
import RelativeAbsoluteSelection from './subComponents/RelativeAbsoluteSelection';
import {isDurationReport} from 'services';

import './BarChartConfig.scss';

export default function BarChartConfig({onChange, report}) {
  const {
    combined,
    data: {configuration},
    result
  } = report;

  return (
    <div className="BarChartConfig">
      {!combined && (
        <fieldset className="ColorSection">
          <legend>Visualization Color</legend>
          <ColorPicker
            selectedColor={configuration.color}
            onChange={color => onChange({color: {$set: color}})}
          />
        </fieldset>
      )}
      <fieldset>
        <legend>Always show tooltips</legend>
        <RelativeAbsoluteSelection
          hideRelative={isDurationReport(combined ? Object.values(result.data)[0] : report)}
          absolute={configuration.alwaysShowAbsolute}
          relative={configuration.alwaysShowRelative}
          onChange={(type, value) => {
            if (type === 'absolute') {
              onChange({alwaysShowAbsolute: {$set: value}});
            } else {
              onChange({alwaysShowRelative: {$set: value}});
            }
          }}
        />
      </fieldset>
      <fieldset className="axisConfig">
        <legend>Axis Labels</legend>
        <LabeledInput
          label="X Axis"
          placeholder="X Axis Label"
          type="text"
          value={configuration.xLabel}
          onChange={({target: {value}}) => onChange({xLabel: {$set: value}})}
        />
        <LabeledInput
          label="Y Axis"
          placeholder="Y Axis Label"
          type="text"
          value={configuration.yLabel}
          onChange={({target: {value}}) => onChange({yLabel: {$set: value}})}
        />
      </fieldset>
      <fieldset className="goalLine">
        <legend>
          <Switch
            checked={configuration.targetValue.active}
            onChange={({target: {checked}}) => onChange({targetValue: {active: {$set: checked}}})}
          />
          Goal
        </legend>
        <ChartTargetInput {...{onChange, report}} />
      </fieldset>
    </div>
  );
}
