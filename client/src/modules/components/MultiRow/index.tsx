/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

type Props = {
  Component?: React.ReactNode;
  rowsToDisplay?: number;
  children?: React.ReactNode;
};

const MultiRow: React.FC<Props> = ({Component, rowsToDisplay, children}) => {
  function rowMultiplier(rowsToDisplay: any) {
    const rows = [];

    for (var i = 0; i < rowsToDisplay; i++) {
      // @ts-expect-error ts-migrate(2604) FIXME: JSX element type 'Component' does not have any con... Remove this comment to see the full error message
      rows.push(<Component key={i} />);
    }
    return rows;
  }

  return (
    <>
      {children}
      {rowMultiplier(rowsToDisplay)}
    </>
  );
};

export default MultiRow;
