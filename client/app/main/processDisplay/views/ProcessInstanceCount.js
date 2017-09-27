import React from 'react';
import {createViewUtilsComponentFromReact} from 'reactAdapter';
import {formatNumber} from 'utils';

const jsx = React.createElement;

export function ProcessInstanceCountReact(props) {
  return (
    <div className="statistics">
      <div className="count">{formatNumber(props.data)}</div>
      <div className="label">Instances</div>
    </div>
  );
}

export const ProcessInstanceCount = createViewUtilsComponentFromReact('div', ProcessInstanceCountReact);
