import React from 'react';
import ShowInstanceCount from './subComponents/ShowInstanceCount';
import {ColorPicker, Switch, LabeledInput} from 'components';
import RelativeAbsoluteSelection from './subComponents/RelativeAbsoluteSelection';
import ChartTargetInput from './subComponents/ChartTargetInput';
import './ChartConfig.scss';

export default function ChartConfig({configuration, onChange, report}) {
  const enabledTarget = configuration.targetValue ? configuration.targetValue.active : false;
  const defaultValues = {target: '', dateFormat: '', isBelow: false};

  const tooltipOptions = (
    <fieldset className="tooltipOptions">
      <legend>Tooltips options</legend>
      <RelativeAbsoluteSelection
        relativeDisabled={report.data.view.property !== 'frequency'}
        configuration={configuration}
        onChange={onChange}
      />
    </fieldset>
  );

  let typeSpecificComponents = [];
  switch (report.data.visualization) {
    case 'line':
      typeSpecificComponents.push(
        <div key="1" className="entry">
          <Switch
            checked={!configuration.pointMarkers}
            onChange={({target: {checked}}) => onChange('pointMarkers', !checked)}
          />
          Disable point markers
        </div>
      );
    /* falls through */
    case 'bar':
      typeSpecificComponents.push(
        <div className="fieldsetsContainer" key="2">
          <fieldset className="ColorSection">
            <legend>Select visualization color</legend>
            <ColorPicker selectedColor={configuration.color} onChange={onChange} />
          </fieldset>
          {tooltipOptions}
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
      break;
    case 'pie':
      typeSpecificComponents.push(
        <div className="fieldsetsContainer" key="2">
          {tooltipOptions}
        </div>
      );
      break;
    default:
      typeSpecificComponents = [];
  }
  return (
    <>
      <ShowInstanceCount configuration={configuration} onChange={onChange} />
      {typeSpecificComponents}
    </>
  );
}

ChartConfig.defaults = {
  showInstanceCount: false,
  color: '#1991c8',
  pointMarkers: true,
  hideRelativeValue: false,
  hideAbsoluteValue: false,
  xLabel: '',
  yLabel: ''
};
