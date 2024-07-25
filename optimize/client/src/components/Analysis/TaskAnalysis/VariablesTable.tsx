/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';

import {Table, NoDataNotice} from 'components';
import {t} from 'translation';

import {
  loadCommonOutliersVariables,
  OutliersVariable,
  AnalysisProcessDefinitionParameters,
  OutlierNode,
} from './service';

interface VariablesTableProps {
  selectedOutlierNode: OutlierNode;
  config: AnalysisProcessDefinitionParameters;
}

export default function VariablesTable({selectedOutlierNode, config}: VariablesTableProps) {
  const [outlierVariables, setOutlierVariables] = useState<OutliersVariable[]>();

  useEffect(() => {
    const loadOutliersVariablesData = async () => {
      const {id, higherOutlier} = selectedOutlierNode;
      const commonSignificantOutliersVariables = await loadCommonOutliersVariables({
        ...config,
        flowNodeId: id,
        higherOutlierBound: higherOutlier.boundValue,
      });
      setOutlierVariables(commonSignificantOutliersVariables);
    };
    loadOutliersVariablesData();
  }, [selectedOutlierNode, config]);

  const constructTableBody = (
    outliersVariables: OutliersVariable[]
  ): (string | JSX.Element)[][] => {
    return outliersVariables.map((row) => [
      row.variableName + '=' + row.variableTerm,
      row.instanceCount.toString(),
      (+(row.outlierToAllInstancesRatio * 100).toFixed(2)).toString(),
      (+(row.outlierRatio * 100).toFixed(2)).toString(),
    ]);
  };

  const tableProps = {
    head: [
      t('report.variables.default').toString(),
      t('analysis.task.detailsModal.table.outliersNumber').toString(),
      t('analysis.task.detailsModal.table.ofTotalPercentage').toString(),
      t('analysis.task.detailsModal.table.ofOutliersPercentage').toString(),
    ],
    body: outlierVariables ? constructTableBody(outlierVariables) : [],
    noData: <NoDataNotice>{t('analysis.task.detailsModal.table.emptyTableMessage')}</NoDataNotice>,
    loading: !outlierVariables,
  };

  return <Table {...tableProps} disablePagination className="VariablesTable" />;
}
