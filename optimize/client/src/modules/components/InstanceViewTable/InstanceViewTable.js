/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useEffect, useCallback, useRef} from 'react';
import {DataTableSkeleton} from '@carbon/react';
import deepEqual from 'fast-deep-equal';

import {useErrorHandling} from 'hooks';
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
        error.reportDefinition && setRawDataReport(error.reportDefinition);
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

function useChangedState(initialState) {
  const [state, _setState] = useState(initialState);

  const setState = useCallback((newState) => {
    _setState((prevState) => (deepEqual(prevState, newState) ? prevState : newState));
  }, []);

  return [state, setState];
}
