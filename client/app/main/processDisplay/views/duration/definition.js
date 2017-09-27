import {jsx, Children, Scope} from 'view-utils';
import {createDiagram} from 'widgets';
import {createDelayedTimePrecisionElement} from 'utils';
import {createHeatmapRendererFunction} from '../frequency';
import {ProcessInstanceCount} from '../ProcessInstanceCount';
import {hasNoHeatmapData, getProcessInstanceCount} from '../service';

const Diagram = createDiagram();

export const definition = {
  id: 'duration',
  name: 'Duration',
  Diagram: () => <Children>
    <Diagram createOverlaysRenderer={createHeatmapRendererFunction(formatDuration)} />
    <Scope selector={getProcessInstanceCount}>
      <ProcessInstanceCount />
    </Scope>
  </Children>,
  hasNoData: hasNoHeatmapData
};

function formatDuration(x) {
  return createDelayedTimePrecisionElement(x, {
    initialPrecision: 2,
    delay: 1500
  });
}
