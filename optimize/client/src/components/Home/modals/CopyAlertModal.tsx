/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Button, Form, TextInput} from '@carbon/react';

import {Modal} from 'components';
import {t} from 'translation';

interface CopyAlertModalProps {
  initialAlertName: string;
  onClose: () => void;
  onConfirm: (name: string) => void;
}

export default function CopyAlertModal({
  onClose,
  initialAlertName,
  onConfirm,
}: CopyAlertModalProps) {
  const [name, setName] = useState(initialAlertName + ' (' + t('common.copyLabel') + ')');

  return (
    <Modal open onClose={onClose}>
      <Modal.Header title={t('common.copyName', {name: initialAlertName})} />
      <Modal.Content>
        <Form>
          <TextInput
            id="copyAlertNameInput"
            labelText={t('home.copy.inputLabel')}
            value={name}
            autoComplete="off"
            onChange={({target: {value}}) => setName(value)}
            data-modal-primary-focus
          />
        </Form>
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button disabled={!name} onClick={() => onConfirm(name)}>
          {t('common.copy')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
