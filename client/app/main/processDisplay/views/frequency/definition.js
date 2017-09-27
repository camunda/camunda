import {jsx, Children, Scope} from 'view-utils';
import {createDiagram} from 'widgets';
import {createHeatmapRendererFunction} from './Heatmap';
import {ProcessInstanceCount} from '../ProcessInstanceCount';
import {hasNoHeatmapData, getProcessInstanceCount} from '../service';

const Diagram = createDiagram();

export const definition = {
  id: 'frequency',
  name: 'Frequency',
  Diagram: () => <Children>
    <Diagram createOverlaysRenderer={createHeatmapRendererFunction(x => x)} />
    <Scope selector={getProcessInstanceCount}>
      <ProcessInstanceCount />
    </Scope>
  </Children>,
  hasNoData: hasNoHeatmapData
};
