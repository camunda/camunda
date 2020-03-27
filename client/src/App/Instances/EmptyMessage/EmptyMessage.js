/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import * as Styled from './styled';

export default function EmptyMessage({message, ...props}) {
  return (
    <Styled.EmptyMessage {...props}>
      {message.split('\n').map((item, index) => (
        <span key={index}>{item}</span>
      ))}
    </Styled.EmptyMessage>
  );
}

EmptyMessage.propTypes = {
  message: PropTypes.string.isRequired,
};
