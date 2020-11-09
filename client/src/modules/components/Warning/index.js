/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {PropTypes} from 'prop-types';
import React from 'react';
import {WarningIcon, Container} from './styled';

const Warning = ({title, ...props}) => (
  <Container title={title} {...props}>
    <WarningIcon>!</WarningIcon>
  </Container>
);

Warning.propTypes = {
  title: PropTypes.string,
};

export {Warning};
