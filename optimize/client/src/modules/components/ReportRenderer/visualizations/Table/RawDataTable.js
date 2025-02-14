/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useState} from 'react';

import {Table as TableRenderer, NoDataNotice} from 'components';
import {t} from 'translation';

import processRawData from './processRawData';
import ColumnRearrangement from './ColumnRearrangement';
import ObjectVariableModal from './ObjectVariableModal';
import {rearrangeColumns} from './service';

export default function RawDataTable({
  report,
  updateReport,
  loadReport,
  updateSorting,
  processVariables,
  loading,
}) {
  const {
    data: {configuration, definitions},
    result,
  } = report;

  const [error, setError] = useState(false);
  const [pageLoading, setPageLoading] = useState(false);
  const [objectVariable, setObjectVariable] = useState();

  const fetchData = useCallback(
    async ({pageIndex, pageSize}) => {
      const offset = pageSize * pageIndex;

      // The backend currently cannot display more than the first 10000 instances
      // TODO: Remove this when #10799 is done
      const maxExceeded = offset >= 10000;
      setError(maxExceeded);
      if (!maxExceeded) {
        setPageLoading(true);
        await loadReport({offset, limit: pageSize});
        setPageLoading(false);
      }
    },
    [loadReport]
  );

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

  const tableProps = {
    ...processRawData({report, processVariables, onVariableView}),
    fetchData,
    loading: pageLoading || loading,
    defaultPageSize: result.pagination.limit,
    defaultPage: result.pagination.offset / result.pagination.limit,
    totalEntries: result.instanceCount,
    errorInPage: error ? (
      <NoDataNotice type="error">{t('report.table.pageError')}</NoDataNotice>
    ) : undefined,
    resultType: result.type,
    sorting: configuration?.sorting,
    updateSorting,
    size: 'md',
  };

  return (
    <>
      <ColumnRearrangement
        enabled={updateReport}
        onChange={(oldIdx, newIdx) => {
          rearrangeColumns(oldIdx, newIdx, tableProps, updateReport);
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
