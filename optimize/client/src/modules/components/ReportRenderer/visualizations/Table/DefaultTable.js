/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Table as TableRenderer} from 'components';

import ColumnRearrangement from './ColumnRearrangement';
import processHyperData from './processHyperData';
import processDefaultData from './processDefaultData';
import {rearrangeColumns} from './service';

export default function DefaultTable(props) {
  const {report, updateReport, processVariables, updateSorting, loading} = props;
  const {
    hyper,
    data: {groupBy, configuration},
    result,
  } = report;

  let tableProps;
  if (hyper) {
    tableProps = processHyperData(props);
  } else {
    tableProps = {
      ...processDefaultData(props, processVariables),
      loading,
      resultType: result.type,
      sorting: configuration?.sorting,
      sortByLabel: ['flowNodes', 'userTasks'].includes(groupBy.type),
      updateSorting,
    };
  }

  return (
    <ColumnRearrangement
      enabled={updateReport}
      onChange={(oldIdx, newIdx) => {
        rearrangeColumns(oldIdx, newIdx, tableProps, updateReport);
      }}
    >
      <TableRenderer size="md" {...tableProps} />
    </ColumnRearrangement>
  );
}
