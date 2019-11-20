/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function Textarea({hasAutoSize, ...props}) {
  return hasAutoSize ? (
    <Styled.TextareaAutosize aria-label={props.placeholder} {...props} />
  ) : (
    <Styled.Textarea aria-label={props.placeholder} {...props} />
  );
}

Textarea.propTypes = {
  placeholder: PropTypes.string,
  hasAutoSize: PropTypes.bool
};
