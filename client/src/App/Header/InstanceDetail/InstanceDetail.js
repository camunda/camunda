/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Fragment} from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function InstanceDetail({instance}) {
  return (
    <Fragment>
      <Styled.StateIcon state={instance.state} /> Instance {instance.id}
    </Fragment>
  );
}

InstanceDetail.propTypes = {
  instance: PropTypes.shape({
    state: PropTypes.string,
    id: PropTypes.string.isRequired
  })
};
