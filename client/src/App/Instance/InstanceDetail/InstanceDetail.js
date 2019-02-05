import React, {Fragment} from 'react';
import PropTypes from 'prop-types';
import {getInstanceState} from 'modules/utils/instance';

import StateIcon from 'modules/components/StateIcon';

export default function InstanceDetail({instance}) {
  return (
    <Fragment>
      <StateIcon state={getInstanceState(instance)} /> Instance {instance.id}
    </Fragment>
  );
}

InstanceDetail.propTypes = {
  instance: PropTypes.shape({
    id: PropTypes.string.isRequired
  })
};
