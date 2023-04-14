/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {Button} from '@carbon/react';

import {LabeledInput, CarbonModal as Modal, Form} from 'components';
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
      <Modal.Header>{t('common.editName', {name: name || id})}</Modal.Header>
      <Modal.Content>
        <Form>
          {t('home.roles.userRole')}
          <Form.Group>
            <LabeledInput
              checked={role === 'viewer'}
              onChange={() => setRole('viewer')}
              label={
                <>
                  <h2>{t('home.roles.viewer')}</h2>
                  <p>{t('home.roles.viewer-description')}</p>
                </>
              }
              type="radio"
            />
            <LabeledInput
              checked={role === 'editor'}
              onChange={() => setRole('editor')}
              label={
                <>
                  <h2>{t('home.roles.editor')}</h2>
                  <p>{t('home.roles.editor-description')}</p>
                </>
              }
              type="radio"
            />
            <LabeledInput
              checked={role === 'manager'}
              onChange={() => setRole('manager')}
              label={
                <>
                  <h2>{t('home.roles.manager')}</h2>
                  <p>{t('home.roles.manager-description')}</p>
                </>
              }
              type="radio"
            />
          </Form.Group>
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
