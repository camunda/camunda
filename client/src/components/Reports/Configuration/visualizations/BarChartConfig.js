import React from 'react';
import {ColorPicker, Switch, LabeledInput} from 'components';
import ChartTargetInput from './subComponents/ChartTargetInput';

export default function BarChartConfig({onChange, report}) {
  const {
    combined,
    data: {configuration}
  } = report;

  return (
    <div className="BarChartConfig">
      {!combined && (
        <fieldset className="ColorSection">
          <legend>Select visualization color</legend>
          <ColorPicker
            selectedColor={configuration.color}
            onChange={color => onChange({color: {$set: color}})}
          />
        </fieldset>
      )}
      <fieldset className="axisConfig">
        <legend>Axis names</legend>
        <LabeledInput
          label="x-axis"
          type="text"
          value={configuration.xLabel}
          onChange={({target: {value}}) => onChange({xLabel: {$set: value}})}
        />
        <LabeledInput
          label="y-axis"
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
