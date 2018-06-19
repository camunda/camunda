import React, {Fragment} from 'react';
import StateIcon from 'modules/components/StateIcon';

export default function InstaceDetail({stateName, instanceId}) {
  return (
    <Fragment>
      <StateIcon stateName={stateName} /> Instance {instanceId}
    </Fragment>
  );
}
