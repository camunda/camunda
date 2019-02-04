import React from 'react';
import BarChartConfig from './BarChartConfig';
import PointMarkersConfig from './subComponents/PointMarkersConfig';

export default function LineChartConfig({onChange, report}) {
  return (
    <>
      <PointMarkersConfig {...{onChange, configuration: report.data.configuration}} />
      <BarChartConfig {...{onChange, report}} />
    </>
  );
}
