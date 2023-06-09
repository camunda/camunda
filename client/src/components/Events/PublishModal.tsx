/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useEffect} from 'react';
import {Button} from '@carbon/react';

import {Modal, Button as LegacyButton, User} from 'components';
import {t} from 'translation';
import {withErrorHandling, WithErrorHandlingProps, withUser, WithUserProps} from 'HOC';
import {showError, addNotification} from 'notifications';

import {publish, getUsers} from './service';
import UsersModal from './UsersModal';

import './PublishModal.scss';

interface PublishModalProps extends WithErrorHandlingProps, WithUserProps {
  id: string;
  republish: boolean;
  onClose: () => void;
  onPublish: (id?: string) => void;
}

export function PublishModal({
  onClose,
  onPublish,
  mightFail,
  id,
  user,
  republish,
}: PublishModalProps): JSX.Element {
  const [loading, setLoading] = useState<boolean>(false);
  const [editingAccess, setEditingAccess] = useState<string | null>(null);
  const [isPrivate, setIsPrivate] = useState<boolean>(false);

  useEffect(() => {
    mightFail(
      getUsers(id),
      (users: User[]) => {
        setIsPrivate(users.length === 1 && users[0]?.identity.id === user?.id);
      },
      showError
    );
  }, [id, mightFail, user?.id]);

  const publishProcess = () => {
    setLoading(true);
    mightFail(
      publish(id),
      () => {
        addNotification({type: 'hint', text: t('events.publishStart')});
        onPublish();
        onClose();
      },
      (error) => {
        showError(error);
      },
      () => setLoading(false)
    );
  };

  const closeUsersModal = (users?: User[] | null) => {
    setEditingAccess(null);
    if (users) {
      setIsPrivate(users.length === 1 && users[0]?.identity.id === user?.id);
    }
  };

  return (
    <Modal open={!!id} onClose={onClose} className="PublishModal">
      <Modal.Header>
        {republish ? t('events.publishModal.republishHead') : t('events.publishModal.head')}
      </Modal.Header>
      <Modal.Content>
        {republish ? (
          <p>{t('events.publishModal.republishText')}</p>
        ) : (
          <p>{t('events.publishModal.text')}</p>
        )}
        <div className="permission">
          <h4>{t('events.permissions.whoHasAccess')}</h4>
          {isPrivate ? t('events.permissions.private') : t('events.permissions.userGranted')}
          <LegacyButton onClick={() => setEditingAccess(id)} link>
            {t('common.change')}...
          </LegacyButton>
        </div>
        {editingAccess && <UsersModal id={editingAccess} onClose={closeUsersModal} />}
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" disabled={loading} className="close" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button disabled={loading} className="confirm" onClick={publishProcess}>
          {t(`events.publish`)}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

export default withUser(withErrorHandling(PublishModal));
