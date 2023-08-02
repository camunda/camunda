/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useEffect, useCallback} from 'react';
import {DataTableSkeleton} from '@carbon/react';
import classNames from 'classnames';

import {useErrorHandling} from 'hooks';
import {evaluateReport} from 'services';
import {ReportRenderer} from 'components';
import {newReport} from 'config';

import './InstanceViewTable.scss';

export default function InstanceViewTable({report, className}) {
  const {mightFail} = useErrorHandling();
  const [rawDataReport, setRawDataReport] = useState();
  const [rawDataError, setRawDataError] = useState();
  const [reportPayload, setReportPayload] = useState(convertToRawData(report));
  const [params, setParams] = useState();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    mightFail(evaluateReport(reportPayload, [], params), setRawDataReport, setRawDataError, () =>
      setLoading(false)
    );
  }, [mightFail, params, reportPayload]);

  const loadRawDataReport = useCallback((params, reportWithUpdatedSorting) => {
    setLoading(true);
    setParams(params);
    if (reportWithUpdatedSorting) {
      setReportPayload(reportWithUpdatedSorting);
    }
  }, []);

  return (
    <div className={classNames('InstanceViewTable', className)}>
      {!rawDataReport && !rawDataError ? (
        <DataTableSkeleton showToolbar={false} showHeader={false} />
      ) : (
        <ReportRenderer
          loading={loading}
          error={rawDataError}
          report={rawDataReport}
          loadReport={loadRawDataReport}
        />
      )}
    </div>
  );
}

function convertToRawData(report) {
  const newType = report.type === 'decision' ? 'new-decision' : 'new';
  const defaultReport = newReport[newType];

  return {
    ...report,
    data: {
      ...report.data,
      configuration: {
        ...defaultReport.data.configuration,
        xml: report.data.configuration.xml,
        sorting: {by: 'startDate', order: 'desc'},
      },
      view: {
        entity: null,
        properties: ['rawData'],
      },
      groupBy: {
        type: 'none',
        value: null,
      },
      visualization: 'table',
    },
  };
}
