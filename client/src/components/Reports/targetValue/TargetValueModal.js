import React from 'react';

import DurationTargetValueModal from './DurationTargetValueModal';
import NumberTargetValueModal from './NumberTargetValueModal';

import {isSingleNumber, isDurationHeatmap} from './service';

export default function TargetValueModal(props) {
  if (isSingleNumber(props.reportResult.data)) {
    return <NumberTargetValueModal {...props} />;
  } else if (isDurationHeatmap(props.reportResult.data)) {
    return <DurationTargetValueModal {...props} />;
  }

  return null;
}
