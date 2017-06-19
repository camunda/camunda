import {jsx, Children} from 'view-utils';
import {TargetValueDisplay} from './TargetValueDisplay';
import {ProcessInstanceCount} from '../ProcessInstanceCount';
import {hasNoHeatmapData} from '../service';

export const definition = {
  id: 'target_value',
  name: 'Target Value Comparison',
  Diagram: () => <Children>
    <TargetValueDisplay />
    <ProcessInstanceCount />
  </Children>,
  hasNoData: hasNoHeatmapData
};
