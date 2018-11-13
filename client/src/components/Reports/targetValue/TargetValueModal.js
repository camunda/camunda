import React from 'react';

import {DurationHeatmapModal} from './DurationHeatmap';
import {ChartModal} from './Chart';

import {isDurationHeatmap, isChart} from './service';

export default function TargetValueModal(props) {
  if (isDurationHeatmap(props.reportResult)) {
    return <DurationHeatmapModal {...props} />;
  } else if (isChart(props.reportResult)) {
    return <ChartModal {...props} />;
  }

  return null;
}
