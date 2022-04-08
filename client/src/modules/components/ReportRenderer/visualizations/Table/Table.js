/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useEffect, useCallback} from 'react';
import update from 'immutability-helper';

import {getReportResult, loadVariables} from 'services';
import {Table as TableRenderer, LoadingIndicator, NoDataNotice} from 'components';
import {withErrorHandling} from 'HOC';
import {getWebappEndpoints} from 'config';
import {t} from 'translation';
import {showError} from 'notifications';

import ColumnRearrangement from './ColumnRearrangement';
import processCombinedData from './processCombinedData';
import processDefaultData from './processDefaultData';
import processRawData from './processRawData';
import ObjectVariableModal from './ObjectVariableModal';

import './Table.scss';

export function Table(props) {
  const {report, updateReport, mightFail, loadReport} = props;
  const {
    reportType,
    combined,
    data: {view, groupBy, configuration, definitions},
    result,
  } = report;

  const isRawDataReport = view?.properties?.[0] === 'rawData';
  const needEndpoint = result && !combined && isRawDataReport;
  const processVariableReport =
    reportType === 'process' && (isRawDataReport || groupBy?.type === 'variable');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [objectVariable, setObjectVariable] = useState();
  const [processVariables, setProcessVariables] = useState();
  const [camundaEndpoints, setCamundaEndpoints] = useState(null);

  const isLoadingEndpoints = needEndpoint && camundaEndpoints === null;
  const isLoadingVariables = processVariableReport && !processVariables;

  useEffect(() => {
    if (needEndpoint) {
      mightFail(getWebappEndpoints(), setCamundaEndpoints);
    }
  }, [mightFail, needEndpoint]);

  useEffect(() => {
    if (processVariableReport) {
      const payload = definitions.map(({key, versions, tenantIds}) => ({
        processDefinitionKey: key,
        processDefinitionVersions: versions,
        tenantIds: tenantIds,
      }));
      mightFail(loadVariables(payload), setProcessVariables, showError);
    }
  }, [definitions, processVariableReport, mightFail, reportType]);

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

      // The backend currently cannot display more than the first 10000 instances
      // TODO: Remove this when OPT-5247 is done
      const maxExceeded = offset >= 10000;
      setError(maxExceeded);
      if (!maxExceeded) {
        setLoading(true);
        await loadReport({offset, limit: pageSize});
        setLoading(false);
      }
    },
    [loadReport]
  );

  if (isLoadingEndpoints || isLoadingVariables) {
    return <LoadingIndicator />;
  }

  const onVariableView = (name, processInstanceId, processDefinitionKey) => {
    const {versions, tenantIds} = definitions.find(({key}) => key === processDefinitionKey);
    setObjectVariable({
      name,
      processInstanceId,
      processDefinitionKey,
      versions,
      tenantIds,
    });
  };

  let tableProps;
  if (combined) {
    tableProps = processCombinedData(props);
  } else {
    let tableData;
    if (isRawDataReport) {
      tableData = processRawData({report, camundaEndpoints, processVariables, onVariableView});
      tableData.fetchData = fetchData;
      tableData.loading = loading;
      tableData.defaultPageSize = result.pagination.limit;
      tableData.defaultPage = result.pagination.offset / result.pagination.limit;
      tableData.totalEntries = result.instanceCount;
      if (error) {
        tableData.error = <NoDataNotice type="error">{t('report.table.pageError')}</NoDataNotice>;
      }
    } else {
      tableData = processDefaultData(props, processVariables);
      tableData.loading = loading;
    }

    tableProps = {
      ...tableData,
      resultType: result.type,
      sorting: configuration && configuration.sorting,
      updateSorting: updateReport && updateSorting,
      sortByLabel: ['flowNodes', 'userTasks'].includes(groupBy.type),
    };
  }

  const isHyper = getReportResult(report)?.type === 'hyperMap';

  return (
    <>
      <ColumnRearrangement
        enabled={updateReport && (isHyper || !report.combined)}
        onChange={(oldIdx, newIdx) => {
          const list = tableProps.head.map((el) => el.id || el);
          // add the column at the specified position
          list.splice(newIdx + 1, 0, list[oldIdx]);
          // remove the original column
          list.splice(oldIdx + (oldIdx > newIdx), 1);
          updateReport({configuration: {tableColumns: {columnOrder: {$set: list}}}});
        }}
      >
        <TableRenderer {...tableProps} />
      </ColumnRearrangement>
      {objectVariable && (
        <ObjectVariableModal
          variable={objectVariable}
          onClose={() => {
            setObjectVariable();
          }}
        />
      )}
    </>
  );
}

export default withErrorHandling(Table);
