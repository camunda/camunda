/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useEffect} from 'react';
import {Button} from '@carbon/react';

import {Modal, UserTypeahead, User} from 'components';
import {showError} from 'notifications';
import {WithErrorHandlingProps, withErrorHandling} from 'HOC';
import {getOptimizeProfile} from 'config';
import {t} from 'translation';

import {getUsers, updateUsers} from './service';

import './UsersModal.scss';

interface UsersModalProps extends WithErrorHandlingProps {
  id: string;
  onClose: (users?: User[] | null) => void;
}

export function UsersModal({id, mightFail, onClose}: UsersModalProps): JSX.Element {
  const [loading, setLoading] = useState<boolean>(false);
  const [users, setUsers] = useState<User[] | null>(null);
  const [optimizeProfile, setOptimizeProfile] = useState<string | null>(null);

  useEffect(() => {
    async function fetchData() {
      mightFail(getUsers(id), setUsers, showError);
      setOptimizeProfile(await getOptimizeProfile());
    }
    fetchData();
  }, [id, mightFail]);

  function onConfirm() {
    setLoading(true);
    mightFail(
      updateUsers(id, users ?? []),
      () => {
        onClose(users);
      },
      (error) => {
        showError(error);
      },
      () => setLoading(false)
    );
  }

  function close() {
    onClose();
  }

  const isValid = users && users.length > 0;

  return (
    <Modal open={!!id} onClose={close} className="UsersModal" isOverflowVisible>
      <Modal.Header title={t('common.editAccess')} />
      <Modal.Content>
        <p className="description">{t('events.permissions.description')}</p>
        <UserTypeahead
          titleText={t('home.userTitle')}
          users={users}
          onChange={setUsers}
          optionsOnly={optimizeProfile === 'cloud'}
        />
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" disabled={loading} onClick={close}>
          {t('common.cancel')}
        </Button>
        <Button disabled={loading || !isValid} onClick={onConfirm}>
          {t('common.save')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

export default withErrorHandling(UsersModal);
