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
