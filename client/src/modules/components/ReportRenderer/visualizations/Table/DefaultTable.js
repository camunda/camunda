/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getReportResult} from 'services';
import {Table as TableRenderer} from 'components';

import ColumnRearrangement from './ColumnRearrangement';
import processCombinedData from './processCombinedData';
import processDefaultData from './processDefaultData';
import {rearrangeColumns} from './service';

export default function DefaultTable(props) {
  const {report, updateReport, processVariables, updateSorting, isSorting} = props;
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
      loading: isSorting,
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
      <TableRenderer {...tableProps} />
    </ColumnRearrangement>
  );
}
