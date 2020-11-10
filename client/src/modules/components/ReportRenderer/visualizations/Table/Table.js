/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect, useCallback} from 'react';

import {Table as TableRenderer, LoadingIndicator} from 'components';
import {withErrorHandling} from 'HOC';
import {getWebappEndpoints} from 'config';

import ColumnRearrangement from './ColumnRearrangement';
import processCombinedData from './processCombinedData';
import processDefaultData from './processDefaultData';
import processRawData from './processRawData';

import './Table.scss';

export function Table(props) {
  const {report, updateReport, mightFail, loadReport} = props;
  const {reportType, combined, data, result} = report;
  const needEndpoint = result && !combined && data.view?.property === 'rawData';

  const [camundaEndpoints, setCamundaEndpoints] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (needEndpoint) {
      mightFail(getWebappEndpoints(), setCamundaEndpoints);
    }
  }, [mightFail, needEndpoint]);

  const updateSorting = (by, order) =>
    updateReport({configuration: {sorting: {$set: {by, order}}}}, true);

  const fetchData = useCallback(
    async ({pageIndex, pageSize}) => {
      const offset = pageSize * pageIndex;

      setLoading(true);
      await loadReport({offset, limit: pageSize});
      setLoading(false);
    },
    [loadReport]
  );

  const formatData = () => {
    const {configuration} = data;

    // Combined Report
    if (combined) {
      return processCombinedData(props);
    }

    let tableData;
    // raw data
    if (data.view.property === 'rawData') {
      tableData = processRawData[reportType](props, camundaEndpoints);
      tableData.fetchData = fetchData;
      tableData.loading = loading;
      tableData.defaultPageSize = result.pagination.limit;
      tableData.totalEntries = result.instanceCount;
    } else {
      // Normal single Report
      tableData = processDefaultData(props);
    }

    return {
      ...tableData,
      resultType: result.type,
      sortByLabel: ['flowNodes', 'userTasks'].includes(data.groupBy.type),
      updateSorting: updateReport && updateSorting,
      sorting: configuration && configuration.sorting,
    };
  };

  if (needEndpoint && camundaEndpoints === null) {
    return <LoadingIndicator />;
  }

  return (
    <ColumnRearrangement report={report} updateReport={updateReport}>
      <TableRenderer {...formatData()} />
    </ColumnRearrangement>
  );
}

export default withErrorHandling(Table);
