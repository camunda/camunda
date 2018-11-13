import React from 'react';
import ShowInstanceCount from './subComponents/ShowInstanceCount';

export default function ChartConfig({configuration, onChange}) {
  return <ShowInstanceCount configuration={configuration} onChange={onChange} />;
}

ChartConfig.defaults = {
  showInstanceCount: false
};
