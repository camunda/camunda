/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';

import {Table, LoadingIndicator, NoDataNotice} from 'components';
import {t} from 'translation';

import {
  loadCommonOutliersVariables,
  OutliersVariable,
  AnalysisProcessDefinitionParameters,
  SelectedNode,
} from './service';

interface VariablesTableProps {
  selectedNode: SelectedNode;
  config: AnalysisProcessDefinitionParameters;
}

export default function VariablesTable({selectedNode, config}: VariablesTableProps) {
  const [data, setData] = useState<OutliersVariable[]>();

  useEffect(() => {
    const loadData = async () => {
      const {id, higherOutlier} = selectedNode;
      const data = await loadCommonOutliersVariables({
        ...config,
        flowNodeId: id,
        higherOutlierBound: higherOutlier.boundValue,
      });
      setData(data);
    };
    loadData();
  }, [selectedNode, config]);

  const constructTableBody = (data: OutliersVariable[]): (string | JSX.Element)[][] => {
    return data.map((row) => [
      row.variableName + '=' + row.variableTerm,
      row.instanceCount.toString(),
      (+(row.outlierToAllInstancesRatio * 100).toFixed(2)).toString(),
      (+(row.outlierRatio * 100).toFixed(2)).toString(),
    ]);
  };

  let tableData;
  if (data?.length) {
    tableData = {
      head: [
        t('report.variables.default').toString(),
        t('analysis.task.detailsModal.table.outliersNumber').toString(),
        t('analysis.task.detailsModal.table.ofTotalPercentage').toString(),
        t('analysis.task.detailsModal.table.ofOutliersPercentage').toString(),
      ],
      body: constructTableBody(data),
    };
  } else {
    tableData = {
      head: [],
      body: [],
      noData: data ? (
        <NoDataNotice>{t('analysis.task.detailsModal.table.emptyTableMessage')}</NoDataNotice>
      ) : (
        <LoadingIndicator />
      ),
    };
  }

  return <Table {...tableData} disablePagination className="VariablesTable" />;
}
