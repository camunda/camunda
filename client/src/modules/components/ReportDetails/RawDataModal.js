/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useCallback, useEffect} from 'react';
import {Button} from '@carbon/react';

import {CarbonModal as Modal, ReportRenderer, LoadingIndicator} from 'components';
import {withErrorHandling} from 'HOC';
import {evaluateReport} from 'services';
import {t} from 'translation';
import {newReport} from 'config';

import './RawDataModal.scss';

export function RawDataModal({name, report, open, onClose, mightFail}) {
  const [rawDataReport, setRawDataReport] = useState();
  const [error, setError] = useState();
  const [reportPayload, setReportPayload] = useState(convertToRawData(report));
  const [params, setParams] = useState();

  useEffect(() => {
    mightFail(evaluateReport(reportPayload, [], params), setRawDataReport, setError);
  }, [mightFail, params, reportPayload]);

  const loadReport = useCallback((params, reportWithUpdatedSorting) => {
    setParams(params);
    if (reportWithUpdatedSorting) {
      setReportPayload(reportWithUpdatedSorting);
    }
  }, []);

  return (
    <Modal className="RawDataModal" open={open} size="lg" onClose={onClose}>
      <Modal.Header>{name}</Modal.Header>
      <Modal.Content>
        {!rawDataReport && !error ? (
          <LoadingIndicator />
        ) : (
          <ReportRenderer error={error} report={rawDataReport} loadReport={loadReport} />
        )}
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" className="close" onClick={onClose}>
          {t('common.close')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

export default withErrorHandling(RawDataModal);

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
