/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Button} from '@carbon/react';

import {BPMNDiagram, CarbonModal as Modal} from 'components';
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
      <Modal.Header>{definitionName}</Modal.Header>
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
