/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Modal, Button, BPMNDiagram, DMNDiagram} from 'components';
import {t} from 'translation';

import './DiagramModal.scss';

export default function DiagramModal({name, report, close}) {
  const {
    data: {
      decisionDefinitionKey,
      configuration: {xml},
    },
  } = report;

  return (
    <Modal className="DiagramModal" open size="max" onClose={close}>
      <Modal.Header>{name}</Modal.Header>
      <Modal.Content>
        {report.reportType === 'decision' ? (
          <DMNDiagram xml={xml} decisionDefinitionKey={decisionDefinitionKey} />
        ) : (
          <BPMNDiagram xml={xml} />
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
