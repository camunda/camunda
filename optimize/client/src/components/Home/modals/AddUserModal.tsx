/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Button, Form, RadioButton, RadioButtonGroup, Stack} from '@carbon/react';

import {Modal, UserTypeahead, User} from 'components';
import {getOptimizeProfile} from 'config';
import {t} from 'translation';

interface Role extends User {
  role: 'viewer' | 'editor' | 'manager';
}

interface AddUserModalProps {
  optimizeProfile: Awaited<ReturnType<typeof getOptimizeProfile>>;
  open: boolean;
  existingUsers: Role[];
  onConfirm: (users: Omit<User, 'id'>[]) => void;
  onClose: () => void;
}

export default function AddUserModal(props: AddUserModalProps) {
  const [users, setUsers] = useState<User[]>([]);
  const [activeRole, setActiveRole] = useState<Role['role']>('viewer');

  const onConfirm = () => {
    if (!users.length) {
      return;
    }

    props.onConfirm(users.map(({identity}) => ({role: activeRole, identity})));
    reset();
  };

  const onClose = () => {
    props.onClose();
    reset();
  };

  const reset = () => {
    setUsers([]);
    setActiveRole('viewer');
  };

  const {optimizeProfile, open, existingUsers} = props;

  return (
    <Modal className="AddUserModal" open={open} onClose={onClose} isOverflowVisible>
      <Modal.Header title={t('home.roles.addUserTitle')} />
      <Modal.Content>
        <Form>
          <Stack gap={6}>
            <UserTypeahead
              titleText={t('home.userTitle')}
              users={users}
              collectionUsers={existingUsers}
              onChange={(users: User[]) => setUsers(users)}
              optionsOnly={optimizeProfile === 'cloud'}
            />
            <RadioButtonGroup
              name="userRole"
              legendText={t('home.roles.userRole')}
              orientation="vertical"
            >
              <RadioButton
                value="viewer"
                checked={activeRole === 'viewer'}
                onClick={() => setActiveRole('viewer')}
                labelText={
                  <>
                    <span>{t('home.roles.viewer')}</span>
                    <span className="subtitle">{t('home.roles.viewer-description')}</span>
                  </>
                }
              />
              <RadioButton
                value="editor"
                checked={activeRole === 'editor'}
                onClick={() => setActiveRole('editor')}
                labelText={
                  <>
                    <span>{t('home.roles.editor')}</span>
                    <span className="subtitle">{t('home.roles.editor-description')}</span>
                  </>
                }
              />
              <RadioButton
                value="manager"
                checked={activeRole === 'manager'}
                onClick={() => setActiveRole('manager')}
                labelText={
                  <>
                    <span>{t('home.roles.manager')}</span>
                    <span className="subtitle">{t('home.roles.manager-description')}</span>
                  </>
                }
              />
            </RadioButtonGroup>
          </Stack>
        </Form>
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" className="cancel" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button className="confirm" disabled={!users.length} onClick={onConfirm}>
          {t('common.add')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
