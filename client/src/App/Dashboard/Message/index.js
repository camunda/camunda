/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import styled from 'styled-components';
import PropTypes from 'prop-types';
import {Container, CheckIcon, WarningIcon} from './styled';

const Message = styled(({children, variant, className}) => {
  return (
    <Container $variant={variant} className={className}>
      {variant === 'success' && <CheckIcon />}
      {variant === 'error' && <WarningIcon />}
      {children}
    </Container>
  );
})``;

Message.propTypes = {
  children: PropTypes.node.isRequired,
  variant: PropTypes.oneOf(['default', 'error', 'success']).isRequired,
  className: PropTypes.string,
};

export {Message};
