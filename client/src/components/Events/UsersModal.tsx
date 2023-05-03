/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useEffect} from 'react';
import {Button} from '@carbon/react';

import {CarbonModal as Modal, UserTypeahead, Labeled, User} from 'components';
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
        setLoading(false);
        onClose(users);
      },
      (error) => {
        showError(error);
        setLoading(false);
      }
    );
  }

  function close() {
    onClose();
  }

  const isValid = users && users.length > 0;

  return (
    <Modal open={!!id} onClose={close} className="UsersModal" isOverflowVisible>
      <Modal.Header>{t('common.editAccess')}</Modal.Header>
      <Modal.Content>
        <p className="description">{t('events.permissions.description')}</p>
        <Labeled className="userTypeahead" label={t('home.userTitle')}>
          {users && (
            <UserTypeahead
              users={users}
              onChange={setUsers}
              optionsOnly={optimizeProfile === 'cloud'}
            />
          )}
        </Labeled>
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
