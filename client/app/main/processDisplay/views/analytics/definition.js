import {jsx, decorateWithSelector, Children} from 'view-utils';
import {createAnalyticsComponents} from './createAnalyticsComponents';
import {Statistics as InnerStatistics} from './statistics';
import {ProcessInstanceCount} from '../ProcessInstanceCount';
import {hasNoHeatmapData} from '../service';

const Statistics = decorateWithSelector(InnerStatistics, getStatisticsState);

const {AnalyticsDiagram, AnalysisSelection} = createAnalyticsComponents();

export const definition = {
  name: 'Branch Analysis',
  Diagram: () => <Children>
    <AnalyticsDiagram />
    <ProcessInstanceCount />
  </Children>,
  Controls: decorateWithSelector(AnalysisSelection, 'analytics'),
  Additional: () => <Statistics getBpmnViewer={AnalyticsDiagram.getViewer} />,
  hasNoData: hasNoHeatmapData
};

function getStatisticsState({analytics: {selection, statistics}}) {
  return {
    ...statistics,
    selection
  };
}
