/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {Button} from '@carbon/react';

import {Modal, BPMNDiagram, Loading} from 'components';
import {WithErrorHandlingProps, withErrorHandling} from 'HOC';
import {t} from 'translation';
import {showError} from 'notifications';
import {loadProcessDefinitionXml} from 'services';

import './DiagramModal.scss';

type Definition = {key: string; name: string; versions: string[]; tenantIds: (string | null)[]};

interface DiagramModalProps extends WithErrorHandlingProps {
  definition: Definition;
  open: boolean;
  onClose: () => void;
}

export function DiagramModal({definition, open, onClose, mightFail}: DiagramModalProps) {
  const [xml, setXml] = useState<string | null>(null);

  useEffect(() => {
    const {key, versions, tenantIds} = definition;
    mightFail(loadProcessDefinitionXml(key, versions[0], tenantIds[0]), setXml, showError);
  }, [mightFail, definition]);

  return (
    <Modal className="DiagramModal" open={open} size="lg" onClose={onClose}>
      <Modal.Header title={definition.name || definition.key} />
      <Modal.Content>
        {!xml && <Loading />}

        <BPMNDiagram xml={xml} />
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
