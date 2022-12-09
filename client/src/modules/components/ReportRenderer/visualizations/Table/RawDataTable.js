/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useCallback, useEffect, useState} from 'react';

import {getWebappEndpoints} from 'config';
import {Table as TableRenderer, LoadingIndicator, NoDataNotice} from 'components';
import {t} from 'translation';

import processRawData from './processRawData';
import ColumnRearrangement from './ColumnRearrangement';
import ObjectVariableModal from './ObjectVariableModal';
import {rearrangeColumns} from './service';

export default function RawDataTable({
  report,
  updateReport,
  mightFail,
  loadReport,
  updateSorting,
  processVariables,
  isSorting,
}) {
  const {
    data: {configuration, definitions},
    result,
  } = report;

  const [error, setError] = useState(false);
  const [loading, setLoading] = useState(false);
  const [objectVariable, setObjectVariable] = useState();
  const [camundaEndpoints, setCamundaEndpoints] = useState(null);

  useEffect(() => {
    if (result) {
      mightFail(getWebappEndpoints(), setCamundaEndpoints);
    }
  }, [mightFail, result]);

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

  if (result && camundaEndpoints === null) {
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

  const tableProps = {
    ...processRawData({report, camundaEndpoints, processVariables, onVariableView}),
    fetchData,
    loading: loading || isSorting,
    defaultPageSize: result.pagination.limit,
    defaultPage: result.pagination.offset / result.pagination.limit,
    totalEntries: result.instanceCount,
    error: error ? (
      <NoDataNotice type="error">{t('report.table.pageError')}</NoDataNotice>
    ) : undefined,
    resultType: result.type,
    sorting: configuration?.sorting,
    updateSorting,
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
