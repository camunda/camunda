import React from 'react';
import {ColorPicker, Switch, LabeledInput} from 'components';
import RelativeAbsoluteSelection from './RelativeAbsoluteSelection';
import ChartTargetInput from './ChartTargetInput';
import './SharedChartConfig.scss';

export default function SharedChartConfig({configuration, onChange, report, hideTooltipOptions}) {
  const enabledTarget = configuration.targetValue ? configuration.targetValue.active : false;
  const defaultValues = {target: '', dateFormat: '', isBelow: false};
  const isSingleReport = report.reportType === 'single';
  const isFrequency = isSingleReport
    ? report.data.view.property === 'frequency'
    : Object.values(report.result)[0].data.view.property === 'frequency';

  return (
    <div className="SharedChartConfig">
      {isSingleReport && (
        <fieldset className="ColorSection">
          <legend>Select visualization color</legend>
          <ColorPicker selectedColor={configuration.color} onChange={onChange} />
        </fieldset>
      )}
      {!hideTooltipOptions && (
        <fieldset className="tooltipOptions">
          <legend>Tooltips options</legend>
          <RelativeAbsoluteSelection
            relativeDisabled={!isFrequency}
            configuration={configuration}
            onChange={onChange}
          />
        </fieldset>
      )}
      <fieldset className="axisConfig">
        <legend>Axis names</legend>
        <LabeledInput
          label="x-axis"
          type="text"
          value={configuration.xLabel}
          onChange={({target: {value}}) => onChange('xLabel', value)}
        />
        <LabeledInput
          label="y-axis"
          type="text"
          value={configuration.yLabel}
          onChange={({target: {value}}) => onChange('yLabel', value)}
        />
      </fieldset>
      <fieldset className="goalLine" disabled={!enabledTarget}>
        <legend>
          <Switch
            checked={enabledTarget}
            onChange={({target: {checked}}) =>
              onChange('targetValue', {
                values: defaultValues,
                ...configuration.targetValue,
                active: checked
              })
            }
          />
          Goal
        </legend>
        <ChartTargetInput {...{configuration, onChange, report}} />
      </fieldset>
    </div>
  );
}
