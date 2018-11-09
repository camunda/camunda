import React from 'react';
import ShowInstanceCount from './ShowInstanceCount';

export default function Chart({configuration, onChange}) {
  return <ShowInstanceCount configuration={configuration} onChange={onChange} />;
}

Chart.defaults = {
  showInstanceCount: false
};
