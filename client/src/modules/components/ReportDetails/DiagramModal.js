/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useState} from 'react';

import {Modal, Button, BPMNDiagram, DMNDiagram, LoadingIndicator} from 'components';
import {withErrorHandling} from 'HOC';
import {t} from 'translation';
import {showError} from 'notifications';
import {loadProcessDefinitionXml, loadDecisionDefinitionXml} from 'services';

import './DiagramModal.scss';

export function DiagramModal({definition, type, close, mightFail}) {
  const [xml, setXml] = useState(null);

  useEffect(() => {
    mightFail(loadXML(type, definition), setXml, showError);
  }, [mightFail, definition, type]);

  return (
    <Modal className="DiagramModal" open size="max" onClose={close}>
      <Modal.Header>{definition.name || definition.key}</Modal.Header>
      <Modal.Content>
        {!xml && <LoadingIndicator />}

        {type === 'decision' ? (
          <DMNDiagram xml={xml} decisionDefinitionKey={definition.key} />
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

function loadXML(reportType, definition) {
  if (reportType === 'decision') {
    const {key, versions, tenantIds} = definition;
    return loadDecisionDefinitionXml(key, versions[0], tenantIds[0]);
  } else {
    const {key, versions, tenantIds} = definition;
    return loadProcessDefinitionXml(key, versions[0], tenantIds[0]);
  }
}
