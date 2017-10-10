import React from 'react';
import {formatNumber} from 'utils';

const jsx = React.createElement;

export function ProcessInstanceCount(props) {
  return (
    <div className="statistics">
      <div className="count">{formatNumber(props.data)}</div>
      <div className="label">Instances</div>
    </div>
  );
}
