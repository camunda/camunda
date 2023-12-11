/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Button, Table, TableBody} from 'components';
import {t} from 'translation';
import {getOutlierSummary} from './service';

import './OutlierDetailsTable.scss';

type TaskData = {totalCount: number; higherOutlier?: {count: number; relation: number}};

type Variable = {variableName: string; variableTerm: string | number | boolean};

interface OutlierDetailsTableProps {
  loading?: boolean;
  tasksData: Record<string, TaskData | undefined>;
  outlierVariables: Record<string, Variable[]>;
  flowNodeNames: Record<string, string>;
  onDetailsClick: (taskId: string, taskData: TaskData) => string;
}

export default function OutlierDetailsTable({
  loading,
  tasksData,
  outlierVariables,
  flowNodeNames,
  onDetailsClick,
}: OutlierDetailsTableProps) {
  function getVariablesList(variables?: Variable[]): string | JSX.Element {
    if (!variables?.length) {
      return '-';
    }

    return (
      <ul>
        {variables.map(({variableName, variableTerm}) => (
          <li key={variableName}>{`${variableName}=${variableTerm}`}</li>
        ))}
      </ul>
    );
  }

  function parseTableBody(): TableBody[] {
    if (!tasksData) {
      return [];
    }

    return Object.entries(tasksData).reduce<TableBody[]>((result, [taskId, taskData]) => {
      if (!taskData || !taskData.higherOutlier) {
        return result;
      }

      const {
        higherOutlier: {count, relation},
        totalCount,
      } = taskData;
      const variables = outlierVariables[taskId];

      result.push([
        flowNodeNames[taskId] || taskId,
        totalCount.toString(),
        getOutlierSummary(count, relation),
        getVariablesList(variables),
        <Button onClick={() => onDetailsClick(taskId, taskData)}>{t('common.viewDetails')}</Button>,
      ]);

      return result;
    }, []);
  }

  return (
    <Table
      className="OutlierDetailsTable"
      head={[
        t('analysis.task.table.taskName').toString(),
        t('analysis.task.totalInstances').toString(),
        t('analysis.task.table.outliers').toString(),
        t('report.variables.default').toString(),
        t('common.details').toString(),
      ]}
      body={parseTableBody()}
      loading={loading}
      disablePagination
    />
  );
}
