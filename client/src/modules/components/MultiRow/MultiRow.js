/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

function MultiRow({Component, rowsToDisplay, children, ...props}) {
  function rowMultiplier(rowsToDisplay) {
    const rows = [];

    for (var i = 0; i < rowsToDisplay; i++) {
      rows.push(<Component key={i} />);
    }
    return rows;
  }

  return (
    <div {...props}>
      {children}
      {rowMultiplier(rowsToDisplay)}
    </div>
  );
}

MultiRow.propTypes = {
  Component: PropTypes.elementType,
  rowsToDisplay: PropTypes.number
};

export default MultiRow;
