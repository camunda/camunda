/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@carbon/react';

import {BPMNDiagram, Modal} from 'components';
import {t} from 'translation';

import './DiagramModal.scss';

interface DiagramModalProps {
  open: boolean;
  onClose?: () => void;
  xml: string;
  definitionName: string;
}

export default function DiagramModal({open, onClose, xml, definitionName}: DiagramModalProps) {
  return (
    <Modal className="DiagramModal" open={open} size="lg" onClose={onClose}>
      <Modal.Header title={definitionName} />
      <Modal.Content>
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
