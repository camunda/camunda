/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useState, useCallback} from 'react';

import {Modal, Button, ReportRenderer, LoadingIndicator} from 'components';
import {withErrorHandling} from 'HOC';
import {evaluateReport} from 'services';
import {t} from 'translation';
import {newReport} from 'config';

import './RawDataModal.scss';

export function RawDataModal({name, report, close, mightFail}) {
  const [rawDataReport, setRawDataReport] = useState();
  const [error, setError] = useState();

  const loadReport = useCallback(
    (query) => {
      const newType = report.type === 'decision' ? 'new-decision' : 'new';
      const defaultReport = newReport[newType];

      mightFail(
        evaluateReport(
          {
            ...report,
            data: {
              ...report.data,
              configuration: {
                ...defaultReport.data.configuration,
                xml: report.data.configuration.xml,
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
          },
          [],
          query
        ),
        setRawDataReport,
        setError
      );
    },
    [mightFail, report]
  );

  useEffect(() => {
    loadReport();
  }, [loadReport]);

  return (
    <Modal className="RawDataModal" open size="max" onClose={close}>
      <Modal.Header>{name}</Modal.Header>
      <Modal.Content>
        {!rawDataReport && !error ? (
          <LoadingIndicator />
        ) : (
          <ReportRenderer error={error} report={rawDataReport} loadReport={loadReport} />
        )}
      </Modal.Content>
      <Modal.Actions>
        <Button main onClick={close}>
          {t('common.close')}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}

export default withErrorHandling(RawDataModal);
