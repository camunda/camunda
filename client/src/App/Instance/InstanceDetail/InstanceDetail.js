/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Fragment} from 'react';
import PropTypes from 'prop-types';

import StateIcon from 'modules/components/StateIcon';

export default function InstanceDetail({instance}) {
  return (
    <Fragment>
      <StateIcon state={instance.state} /> Instance {instance.id}
    </Fragment>
  );
}

InstanceDetail.propTypes = {
  instance: PropTypes.shape({
    id: PropTypes.string.isRequired
  })
};
