import React from 'react';
const jsx = React.createElement;

import {createAnalyticsComponents} from './createAnalyticsComponents';
import {Statistics} from './statistics';
import {ProcessInstanceCount} from '../ProcessInstanceCount';
import {hasNoHeatmapData, getProcessInstanceCount} from '../service';

const {AnalyticsDiagram, AnalysisSelection} = createAnalyticsComponents();

export const definition = {
  id: 'branch_analysis',
  name: 'Branch Analysis',
  Diagram: props => {
    return (<div>
      <AnalyticsDiagram {...props} />
      <ProcessInstanceCount {...getProcessInstanceCount(props)} />
    </div>);
  },
  Controls: props => <AnalysisSelection {...props.analytics} />,
  Additional: props => {
    return props.analytics && <Statistics {...getStatisticsState(props)} getBpmnViewer={AnalyticsDiagram.getViewer} /> || null;
  },
  hasNoData: hasNoHeatmapData
};

function getStatisticsState({analytics: {selection, statistics}}) {
  return {
    ...statistics,
    selection
  };
}
