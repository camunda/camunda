import React from 'react';
import {Switch} from 'components';

export default function PointMarkersConfig({configuration, onChange}) {
  return (
    <div className="PointMarkersConfig">
      <Switch
        checked={!configuration.pointMarkers}
        onChange={({target: {checked}}) => onChange({pointMarkers: {$set: !checked}})}
      />
      Disable point markers
    </div>
  );
}
