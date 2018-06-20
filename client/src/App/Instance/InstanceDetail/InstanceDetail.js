import React, {Fragment} from 'react';
import StateIcon from 'modules/components/StateIcon';

export default function InstaceDetail({instance}) {
  return (
    <Fragment>
      <StateIcon instance={instance} /> Instance {instance.id}
    </Fragment>
  );
}
