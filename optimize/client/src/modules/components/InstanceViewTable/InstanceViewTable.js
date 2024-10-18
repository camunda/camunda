/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect, useCallback, useRef} from 'react';
import {DataTableSkeleton} from '@carbon/react';

import {useChangedState, useErrorHandling} from 'hooks';
import {evaluateReport} from 'services';
import {ReportRenderer} from 'components';
import {newReport} from 'config';

import './InstanceViewTable.scss';

export default function InstanceViewTable({report}) {
  const {mightFail} = useErrorHandling();
  const [rawDataReport, setRawDataReport] = useChangedState();
  const [rawDataError, setRawDataError] = useState();

  const [reportPayload, setReportPayload] = useChangedState(convertToRawData(report));
  const [params, setParams] = useChangedState();
  const [loading, setLoading] = useState(false);
  const prevReportRef = useRef(report);

  useEffect(() => {
    setReportPayload(convertToRawData(report));
    prevReportRef.current = report;
  }, [report, setReportPayload]);

  useEffect(() => {
    setLoading(true);
    mightFail(
      evaluateReport(reportPayload, [], params),
      (newReportData) => {
        setRawDataReport(newReportData);
        setRawDataError();
      },
      (error) => {
        setRawDataError(error);
        if (error.reportDefinition) {
          setRawDataReport(error.reportDefinition);
        }
      },
      () => setLoading(false)
    );
  }, [mightFail, params, reportPayload, setRawDataReport]);

  const loadRawDataReport = useCallback(
    (params, reportWithUpdatedSorting) => {
      setParams(params);
      if (reportWithUpdatedSorting) {
        setReportPayload(reportWithUpdatedSorting);
      }
    },
    [setParams, setReportPayload]
  );

  return (
    <div className="InstanceViewTable">
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
  const defaultReport = newReport['new'];

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
