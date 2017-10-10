import React from 'react';
const jsx = React.createElement;

import {createDiagram} from 'widgets';
import {createHeatmapRendererFunction} from './Heatmap';
import {ProcessInstanceCount} from '../ProcessInstanceCount';
import {hasNoHeatmapData, getProcessInstanceCount} from '../service';

const Diagram = createDiagram();

export const definition = {
  id: 'frequency',
  name: 'Frequency',
  Diagram: props => {
    return (<div>
      <Diagram createOverlaysRenderer={createHeatmapRendererFunction(x => x)} {...props} />
      <ProcessInstanceCount {...getProcessInstanceCount(props)} />
    </div>);
  },
  hasNoData: hasNoHeatmapData
};
