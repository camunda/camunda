/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {TableSelectRow, TableCell as CarbonTableCell} from '@carbon/react';
import classnames from 'classnames';
import {Cell} from 'react-table';

import {Select} from 'components';

interface TableCellProps<T extends object> {
  cell: Cell<T>;
}

export default function TableCell<T extends object>({cell}: TableCellProps<T>): JSX.Element {
  if (cell.value?.type === TableSelectRow) {
    return <>{cell.render('Cell', {key: cell.value.props.id})}</>;
  }

  const {className, key, ...props} = cell.getCellProps();
  return (
    <CarbonTableCell
      key={key}
      {...props}
      className={classnames(className, {
        noOverflow: cell.value?.type === Select,
      })}
    >
      {cell.render('Cell')}
    </CarbonTableCell>
  );
}
