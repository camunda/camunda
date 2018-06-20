import React, {Fragment} from 'react';
import PropTypes from 'prop-types';

import StateIcon from 'modules/components/StateIcon';

export default function InstanceDetail({instance}) {
  return (
    <Fragment>
      <StateIcon instance={instance} /> Instance {instance.id}
    </Fragment>
  );
}

InstanceDetail.propTypes = {
  instance: PropTypes.shape({
    id: PropTypes.string.isRequired
  })
};
