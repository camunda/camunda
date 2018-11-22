import React from 'react';
import ShowInstanceCount from './subComponents/ShowInstanceCount';
import RelativeAbsoluteSelection from './subComponents/RelativeAbsoluteSelection';
import SharedChartConfig from './subComponents/SharedChartConfig';
import PointMarkersConfig from './subComponents/PointMarkersConfig';

export default function ChartConfig({configuration, onChange, report}) {
  const visualization = report.data.visualization;

  return (
    <>
      <ShowInstanceCount configuration={configuration} onChange={onChange} />

      {visualization === 'line' && <PointMarkersConfig {...{configuration, onChange}} />}

      {['line', 'bar'].includes(visualization) && (
        <SharedChartConfig {...{configuration, onChange, report}} />
      )}

      {visualization === 'pie' && (
        <fieldset className="tooltipOptions">
          <legend>Tooltips options</legend>
          <RelativeAbsoluteSelection
            relativeDisabled={report.data.view.property !== 'frequency'}
            configuration={configuration}
            onChange={onChange}
          />
        </fieldset>
      )}
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
  yLabel: '',
  targetValue: null
};

ChartConfig.onUpdate = (prevProps, props) => {
  const currentView = props.report.data.view;
  const prevView = prevProps.report.data.view;
  if (
    currentView.property !== prevView.property ||
    currentView.entity !== prevView.entity ||
    (prevProps.type !== props.type && !isBarOrLine(prevProps.type, props.type))
  ) {
    return ChartConfig.defaults;
  }
};

function isBarOrLine(currentVis, nextVis) {
  const barOrLine = ['bar', 'line'];
  return barOrLine.includes(currentVis) && barOrLine.includes(nextVis);
}
