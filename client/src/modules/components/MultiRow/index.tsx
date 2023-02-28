/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

type Props = {
  Component: React.ComponentType;
  rowsToDisplay?: number;
  children?: React.ReactNode;
};

const MultiRow: React.FC<Props> = ({Component, rowsToDisplay, children}) => {
  function rowMultiplier(rowsToDisplay: number) {
    const rows = [];

    for (let i = 0; i < rowsToDisplay; i++) {
      rows.push(<Component key={i} />);
    }
    return rows;
  }

  return (
    <>
      {children}
      {rowsToDisplay !== undefined &&
        rowsToDisplay !== 0 &&
        rowMultiplier(rowsToDisplay)}
    </>
  );
};

export {MultiRow};
