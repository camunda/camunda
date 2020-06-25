/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Modal, Button, BPMNDiagram, ReportRenderer} from 'components';
import {t} from 'translation';

import defaults from './newReport.json';

import './DiagramModal.scss';

export default function DiagramModal({name, report, close}) {
  return (
    <Modal className="DiagramModal" open size="max" onClose={close}>
      <Modal.Header>{name}</Modal.Header>
      <Modal.Content>
        {report.reportType === 'decision' ? (
          <ReportRenderer
            hitsHidden
            report={{
              ...report,
              data: {
                ...report.data,
                configuration: {
                  ...defaults['new-decision'].data.configuration,
                  xml: report.data.configuration.xml,
                },
                view: {
                  property: 'frequency',
                },
                groupBy: {
                  type: 'matchedRule',
                },
                visualization: 'table',
              },
              result: {
                ...report.result,
                instanceCount: 1,
                data: [{}],
              },
            }}
          />
        ) : (
          <BPMNDiagram xml={report.data.configuration.xml} />
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
