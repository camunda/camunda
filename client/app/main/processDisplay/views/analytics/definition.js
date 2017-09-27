import {jsx, decorateWithSelector, Children, Scope} from 'view-utils';
import {createAnalyticsComponents} from './createAnalyticsComponents';
import {Statistics as InnerStatistics} from './statistics';
import {ProcessInstanceCount} from '../ProcessInstanceCount';
import {hasNoHeatmapData, getProcessInstanceCount} from '../service';

const Statistics = decorateWithSelector(InnerStatistics, getStatisticsState);

const {AnalyticsDiagram, AnalysisSelection} = createAnalyticsComponents();

export const definition = {
  id: 'branch_analysis',
  name: 'Branch Analysis',
  Diagram: () => <Children>
    <AnalyticsDiagram />
    <Scope selector={getProcessInstanceCount}>
      <ProcessInstanceCount />
    </Scope>
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
