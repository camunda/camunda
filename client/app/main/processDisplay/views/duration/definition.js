import {jsx, Children} from 'view-utils';
import {createDiagram} from 'widgets';
import {createDelayedTimePrecisionElement} from 'utils';
import {createHeatmapRendererFunction} from '../frequency';
import {ProcessInstanceCount} from '../ProcessInstanceCount';
import {hasNoHeatmapData} from '../service';

const Diagram = createDiagram();

export const definition = {
  id: 'duration',
  name: 'Duration',
  Diagram: () => <Children>
    <Diagram createOverlaysRenderer={createHeatmapRendererFunction(formatDuration)} />
    <ProcessInstanceCount />
  </Children>,
  hasNoData: hasNoHeatmapData
};

function formatDuration(x) {
  return createDelayedTimePrecisionElement(x, {
    initialPrecision: 2,
    delay: 1500
  });
}
