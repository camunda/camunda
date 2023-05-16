/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {Button} from '@carbon/react';

import {LabeledInput, Modal, Form, UserTypeahead, User} from 'components';
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
      <Modal.Header>
        {optimizeProfile === 'platform'
          ? t('home.roles.addUserGroupTitle')
          : t('home.roles.addUserTitle')}
      </Modal.Header>
      <Modal.Content>
        <Form>
          {optimizeProfile === 'platform' ? t('home.userGroupsTitle') : t('home.userTitle')}
          <Form.Group>
            <UserTypeahead
              users={users}
              collectionUsers={existingUsers}
              onChange={(users: User[]) => setUsers(users)}
              optionsOnly={optimizeProfile === 'cloud'}
            />
          </Form.Group>
          {t('home.roles.userRole')}
          <Form.Group>
            <LabeledInput
              checked={activeRole === 'viewer'}
              onChange={() => setActiveRole('viewer')}
              label={
                <>
                  <h2>{t('home.roles.viewer')}</h2>
                  <p>{t('home.roles.viewer-description')}</p>
                </>
              }
              type="radio"
            />
            <LabeledInput
              checked={activeRole === 'editor'}
              onChange={() => setActiveRole('editor')}
              label={
                <>
                  <h2>{t('home.roles.editor')}</h2>
                  <p>{t('home.roles.editor-description')}</p>
                </>
              }
              type="radio"
            />
            <LabeledInput
              checked={activeRole === 'manager'}
              onChange={() => setActiveRole('manager')}
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
        <Button className="confirm" disabled={!users.length} onClick={onConfirm}>
          {t('common.add')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
