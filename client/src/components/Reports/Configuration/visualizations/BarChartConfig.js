import React from 'react';
import {ColorPicker, Switch, LabeledInput} from 'components';
import RelativeAbsoluteSelection from './subComponents/RelativeAbsoluteSelection';
import ChartTargetInput from './subComponents/ChartTargetInput';
import ShowInstanceCount from './subComponents/ShowInstanceCount';

export default function BarChartConfig({configuration, onChange, report}) {
  const enabledTarget = configuration.targetValue ? configuration.targetValue.active : false;
  const defaultValues = {target: '', dateFormat: '', isBelow: false};
  const isSingleReport = !report.combined;

  const isFrequency = isSingleReport
    ? report.data.view.property === 'frequency'
    : Object.values(report.result)[0].data.view.property === 'frequency';

  const isCombinedNumber =
    !isSingleReport && Object.values(report.result)[0].data.visualization === 'number';

  return (
    <div className="BarChartConfig">
      {isSingleReport && (
        <>
          <ShowInstanceCount configuration={configuration} onChange={onChange} />
          <fieldset className="ColorSection">
            <legend>Select visualization color</legend>
            <ColorPicker
              selectedColor={configuration.color[0]}
              onChange={color => onChange('color', [color])}
            />
          </fieldset>
        </>
      )}
      {!isCombinedNumber && (
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
      <fieldset className="goalLine">
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

BarChartConfig.defaults = ({report}) => {
  return {
    ...(report.combined ? {} : {color: [ColorPicker.dark.steelBlue], showInstanceCount: false}),
    hideRelativeValue: false,
    hideAbsoluteValue: false,
    xLabel: '',
    yLabel: '',
    targetValue: null
  };
};

BarChartConfig.onUpdate = (prevProps, props) => {
  if (props.report.combined) return prevProps.type !== props.type && BarChartConfig.defaults(props);
  const currentView = props.report.data.view;
  const prevView = prevProps.report.data.view;
  if (
    currentView.property !== prevView.property ||
    currentView.entity !== prevView.entity ||
    (prevProps.type !== props.type && !isBarOrLine(prevProps.type, props.type))
  ) {
    return BarChartConfig.defaults(props);
  }
};

function isBarOrLine(currentVis, nextVis) {
  const barOrLine = ['bar', 'line'];
  return barOrLine.includes(currentVis) && barOrLine.includes(nextVis);
}
