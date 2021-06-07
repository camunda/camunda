/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useState} from 'react';

import {Modal, Button, BPMNDiagram, DMNDiagram, LoadingIndicator} from 'components';
import {withErrorHandling} from 'HOC';
import {t} from 'translation';
import {showError} from 'notifications';
import {loadProcessDefinitionXml, loadDecisionDefinitionXml} from 'services';

import './DiagramModal.scss';

export function DiagramModal({name, report, close, mightFail}) {
  const {
    reportType,
    data: {
      definitions: [{key}],
    },
  } = report;

  const [xml, setXml] = useState(null);

  useEffect(() => {
    mightFail(report.data.configuration?.xml ?? loadXML(report), setXml, showError);
  }, [mightFail, report]);

  return (
    <Modal className="DiagramModal" open size="max" onClose={close}>
      <Modal.Header>{name}</Modal.Header>
      <Modal.Content>
        {!xml && <LoadingIndicator />}

        {reportType === 'decision' ? (
          <DMNDiagram xml={xml} decisionDefinitionKey={key} />
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

export default withErrorHandling(DiagramModal);

function loadXML({reportType, data}) {
  if (reportType === 'decision') {
    const {key, versions, tenantIds} = data.definitions[0];
    return loadDecisionDefinitionXml(key, versions[0], tenantIds[0]);
  } else {
    const {key, versions, tenantIds} = data.definitions[0];
    return loadProcessDefinitionXml(key, versions[0], tenantIds[0]);
  }
}
