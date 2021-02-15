/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect, useCallback} from 'react';
import update from 'immutability-helper';

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
  const needEndpoint = result && !combined && data.view?.properties[0] === 'rawData';

  const [camundaEndpoints, setCamundaEndpoints] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (needEndpoint) {
      mightFail(getWebappEndpoints(), setCamundaEndpoints);
    }
  }, [mightFail, needEndpoint]);

  const updateSorting = async (by, order) => {
    setLoading(true);
    await loadReport(result.pagination, {
      ...report,
      data: update(report.data, {configuration: {sorting: {$set: {by, order}}}}),
    });
    setLoading(false);
  };

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
    if (data.view.properties[0] === 'rawData') {
      tableData = processRawData[reportType](props, camundaEndpoints);
      tableData.fetchData = fetchData;
      tableData.loading = loading;
      tableData.defaultPageSize = result.pagination.limit;
      tableData.defaultPage = result.pagination.offset / result.pagination.limit;
      tableData.totalEntries = result.instanceCount;
    } else {
      // Normal single Report
      tableData = processDefaultData(props);
      tableData.loading = loading;
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
