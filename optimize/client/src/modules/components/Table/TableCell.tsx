/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

  const {className, ...props} = cell.getCellProps();
  return (
    <CarbonTableCell
      {...props}
      className={classnames(className, {
        noOverflow: cell.value?.type === Select,
      })}
    >
      {cell.render('Cell')}
    </CarbonTableCell>
  );
}
