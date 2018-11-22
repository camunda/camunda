import React from 'react';
import RelativeAbsoluteSelection from './subComponents/RelativeAbsoluteSelection';
import SharedChartConfig from './subComponents/SharedChartConfig';
import PointMarkersConfig from './subComponents/PointMarkersConfig';

export default function CombinedReportConfig({configuration, onChange, report}) {
  const visualization = getVisualization(report);

  return (
    <>
      {visualization === 'line' && <PointMarkersConfig {...{configuration, onChange}} />}

      {['line', 'number', 'bar'].includes(visualization) && (
        <SharedChartConfig
          {...{configuration, onChange, report, hideTooltipOptions: visualization === 'number'}}
        />
      )}

      {visualization === 'table' && (
        <RelativeAbsoluteSelection configuration={configuration} onChange={onChange} />
      )}
    </>
  );
}

CombinedReportConfig.defaults = {
  showInstanceCount: false,
  color: '#1991c8',
  pointMarkers: true,
  hideRelativeValue: false,
  hideAbsoluteValue: false,
  xLabel: '',
  yLabel: '',
  targetValue: null
};

CombinedReportConfig.onUpdate = (prevProps, props) => {
  const isCombinedEmpty = ({report}) => !report.data.reportIds || !report.data.reportIds.length;

  const prevVis = isCombinedEmpty(prevProps) ? '' : getVisualization(prevProps.report);
  const vis = isCombinedEmpty(props) ? '' : getVisualization(props.report);

  if (prevVis !== vis) return CombinedReportConfig.defaults;
};

function getVisualization(report) {
  return Object.values(report.result)[0].data.visualization;
}
