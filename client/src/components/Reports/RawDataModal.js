/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useState} from 'react';

import {Modal, Button, ReportRenderer, LoadingIndicator} from 'components';
import {withErrorHandling} from 'HOC';
import {evaluateReport} from 'services';
import {showError} from 'notifications';
import {t} from 'translation';

import defaults from './newReport.json';

import './RawDataModal.scss';

export function RawDataModal({name, report, close, mightFail}) {
  const [rawDataReport, setRawDataReport] = useState();

  useEffect(() => {
    const newType = report.type === 'decision' ? 'new-decision' : 'new';
    const defaultReport = defaults[newType];

    mightFail(
      evaluateReport({
        ...report,
        data: {
          ...report.data,
          configuration: {
            ...defaultReport.data.configuration,
            xml: report.data.configuration.xml,
          },
          view: {
            entity: null,
            property: 'rawData',
          },
          groupBy: {
            type: 'none',
            value: null,
          },
          visualization: 'table',
        },
      }),
      setRawDataReport,
      showError
    );
  }, [mightFail, report]);

  return (
    <Modal className="RawDataModal" open size="max" onClose={close}>
      <Modal.Header>{name}</Modal.Header>
      <Modal.Content>
        {!rawDataReport ? <LoadingIndicator /> : <ReportRenderer report={rawDataReport} />}
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
