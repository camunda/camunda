/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getReportResult} from 'services';
import {Table as TableRenderer} from 'components';

import ColumnRearrangement from './ColumnRearrangement';
import processCombinedData from './processCombinedData';
import processDefaultData from './processDefaultData';
import {rearrangeColumns} from './service';

export default function DefaultTable(props) {
  const {report, updateReport, processVariables, updateSorting, loading} = props;
  const {
    combined,
    data: {groupBy, configuration},
    result,
  } = report;

  let tableProps;
  if (combined) {
    tableProps = processCombinedData(props);
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

  const isHyper = getReportResult(report)?.type === 'hyperMap';
  return (
    <ColumnRearrangement
      enabled={updateReport && (isHyper || !report.combined)}
      onChange={(oldIdx, newIdx) => {
        rearrangeColumns(oldIdx, newIdx, tableProps, updateReport);
      }}
    >
      <TableRenderer size="md" {...tableProps} />
    </ColumnRearrangement>
  );
}
