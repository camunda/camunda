/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Button, RadioButton, Form, RadioButtonGroup} from '@carbon/react';

import {Modal} from 'components';
import {t} from 'translation';

interface EditUserModalProps {
  identity: {name: string; id: string};
  initialRole: string;
  onClose: () => void;
  onConfirm: (name: string) => void;
}

export default function EditUserModal({
  identity,
  initialRole,
  onClose,
  onConfirm,
}: EditUserModalProps) {
  const {name, id} = identity;
  const [role, setRole] = useState(initialRole);

  return (
    <Modal className="EditUserModal" open onClose={onClose}>
      <Modal.Header title={t('common.editName', {name: name || id})} />
      <Modal.Content>
        <Form>
          <RadioButtonGroup
            name="userRole"
            legendText={t('home.roles.userRole')}
            orientation="vertical"
          >
            <RadioButton
              value="viewer"
              checked={role === 'viewer'}
              onClick={() => setRole('viewer')}
              labelText={
                <>
                  <span>{t('home.roles.viewer')}</span>
                  <span className="subtitle">{t('home.roles.viewer-description')}</span>
                </>
              }
              type="radio"
            />
            <RadioButton
              value="editor"
              checked={role === 'editor'}
              onClick={() => setRole('editor')}
              labelText={
                <>
                  <span>{t('home.roles.editor')}</span>
                  <span className="subtitle">{t('home.roles.editor-description')}</span>
                </>
              }
              type="radio"
            />
            <RadioButton
              value="manager"
              checked={role === 'manager'}
              onClick={() => setRole('manager')}
              labelText={
                <>
                  <span>{t('home.roles.manager')}</span>
                  <span className="subtitle">{t('home.roles.manager-description')}</span>
                </>
              }
              type="radio"
            />
          </RadioButtonGroup>
        </Form>
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" className="cancel" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button className="confirm" onClick={() => onConfirm(role)}>
          {t('common.apply')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
