import React from 'react';
const jsx = React.createElement;

import {createDiagram} from 'widgets';
import {createAnalysisSelection} from './AnalysisSelection';
import {createCreateAnalyticsRendererFunction} from './Analytics';
import {getNameForElement} from './service';

export function createAnalyticsComponents() {
  const Diagram = createDiagram();
  const AnalysisSelection = createAnalysisSelection(
    getNameForElement.bind(null, Diagram)
  );
  const createAnalyticsRenderer = createCreateAnalyticsRendererFunction();
  const AnalyticsDiagram = props => <Diagram {...props} createOverlaysRenderer={createAnalyticsRenderer} />;

  AnalyticsDiagram.getViewer = Diagram.getViewer;

  return {AnalyticsDiagram, AnalysisSelection};
}
