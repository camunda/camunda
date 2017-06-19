import {jsx, Children} from 'view-utils';
import {createDiagram} from 'widgets';
import {createHeatmapRendererFunction} from './Heatmap';
import {ProcessInstanceCount} from '../ProcessInstanceCount';
import {hasNoHeatmapData} from '../service';

const Diagram = createDiagram();

export const definition = {
  id: 'frequency',
  name: 'Frequency',
  Diagram: () => <Children>
    <Diagram createOverlaysRenderer={createHeatmapRendererFunction(x => x)} />
    <ProcessInstanceCount />
  </Children>,
  hasNoData: hasNoHeatmapData
};
