import {jsx, Children, Scope} from 'view-utils';
import {TargetValueDisplay} from './TargetValueDisplay';
import {ProcessInstanceCount} from '../ProcessInstanceCount';
import {hasNoHeatmapData, getProcessInstanceCount} from '../service';

export const definition = {
  id: 'target_value',
  name: 'Target Value Comparison',
  Diagram: () => <Children>
    <TargetValueDisplay />
    <Scope selector={getProcessInstanceCount}>
      <ProcessInstanceCount />
    </Scope>
  </Children>,
  hasNoData: hasNoHeatmapData
};
