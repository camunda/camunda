import React from 'react';
const jsx = React.createElement;

import {createDiagram} from 'widgets';
import {createDelayedTimePrecisionElement} from 'utils';
import {createHeatmapRendererFunction} from '../frequency';
import {ProcessInstanceCount} from '../ProcessInstanceCount';
import {hasNoHeatmapData, getProcessInstanceCount} from '../service';

const Diagram = createDiagram();

export const definition = {
  id: 'duration',
  name: 'Duration',
  Diagram: props => {
    return (<div>
      <Diagram createOverlaysRenderer={createHeatmapRendererFunction(formatDuration)} {...props} />
      <ProcessInstanceCount {...getProcessInstanceCount(props)} />
    </div>);
  },
  hasNoData: hasNoHeatmapData
};

function formatDuration(x) {
  return createDelayedTimePrecisionElement(x, {
    initialPrecision: 2,
    delay: 1500
  });
}
