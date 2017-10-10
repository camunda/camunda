import React from 'react';
const jsx = React.createElement;

import {TargetValueDisplay} from './TargetValueDisplay';
import {ProcessInstanceCount} from '../ProcessInstanceCount';
import {hasNoHeatmapData, getProcessInstanceCount} from '../service';

export const definition = {
  id: 'target_value',
  name: 'Target Value Comparison',
  Diagram: props => {
    return (<div>
      <TargetValueDisplay {...props} />
      <ProcessInstanceCount {...getProcessInstanceCount(props)} />
    </div>);
  },
  hasNoData: hasNoHeatmapData
};
