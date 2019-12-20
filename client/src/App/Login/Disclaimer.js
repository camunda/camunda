/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';
export default function Disclaimer(props) {
  return props.isEnterprise ? null : (
    <Styled.Disclaimer>
      This Camunda Operate distribution is available under an evaluation license
      that is valid for development (non-production) use only. By continuing
      using this software, you agree to the{' '}
      <Styled.Anchor href="https://zeebe.io/legal/operate-evaluation-license">
        Terms and Conditions
      </Styled.Anchor>{' '}
      of the Operate Trial Version.
    </Styled.Disclaimer>
  );
}

Disclaimer.propTypes = {
  isEnterprise: PropTypes.bool
};
