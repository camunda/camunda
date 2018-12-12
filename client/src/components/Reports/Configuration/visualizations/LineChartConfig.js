import React from 'react';
import BarChartConfig from './BarChartConfig';
import PointMarkersConfig from './subComponents/PointMarkersConfig';

export default function LineChartConfig({configuration, onChange, report}) {
  return (
    <>
      <PointMarkersConfig {...{configuration, onChange}} />
      <BarChartConfig {...{configuration, onChange, report}} />
    </>
  );
}
LineChartConfig.defaults = props => {
  return {
    pointMarkers: true,
    ...BarChartConfig.defaults(props)
  };
};

LineChartConfig.onUpdate = (prevProps, props) => {
  return BarChartConfig.onUpdate(prevProps, props) && LineChartConfig.defaults(props);
};
