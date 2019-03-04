/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled.js';

export default function MetricPanel({children}) {
  return (
    <Styled.Panel>
      <Styled.Ul>
        {children.map((child, index) => {
          return <li key={index}>{child}</li>;
        })}
      </Styled.Ul>
    </Styled.Panel>
  );
}

MetricPanel.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ])
};
