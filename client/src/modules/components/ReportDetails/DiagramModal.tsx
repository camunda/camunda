/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';
import {Button} from '@carbon/react';

import {CarbonModal as Modal, BPMNDiagram, DMNDiagram, LoadingIndicator} from 'components';
import {WithErrorHandlingProps, withErrorHandling} from 'HOC';
import {t} from 'translation';
import {showError} from 'notifications';
import {loadProcessDefinitionXml, loadDecisionDefinitionXml} from 'services';

import './DiagramModal.scss';

type Definition = {key: string; name: string; versions: string[]; tenantIds: (string | null)[]};

interface DiagramModalProps extends WithErrorHandlingProps {
  definition: Definition;
  type: string;
  open: boolean;
  onClose: () => void;
}

export function DiagramModal({definition, type, open, onClose, mightFail}: DiagramModalProps) {
  const [xml, setXml] = useState<string | null>(null);

  useEffect(() => {
    mightFail(loadXML(type, definition), setXml, showError);
  }, [mightFail, definition, type]);

  return (
    <Modal className="DiagramModal" open={open} size="lg" onClose={onClose}>
      <Modal.Header>{definition.name || definition.key}</Modal.Header>
      <Modal.Content>
        {!xml && <LoadingIndicator />}

        {type === 'decision' ? (
          <DMNDiagram xml={xml} decisionDefinitionKey={definition.key} />
        ) : (
          <BPMNDiagram xml={xml} />
        )}
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" onClick={onClose}>
          {t('common.close')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

export default withErrorHandling(DiagramModal);

function loadXML(reportType: string, definition: Definition) {
  const {key, versions, tenantIds} = definition;

  if (reportType === 'decision') {
    return loadDecisionDefinitionXml(key, versions[0], tenantIds[0]);
  } else {
    return loadProcessDefinitionXml(key, versions[0], tenantIds[0]);
  }
}
