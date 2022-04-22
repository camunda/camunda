/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useState} from 'react';

import {Button, Deleter, Labeled, Modal, UserTypeahead} from 'components';
import {t} from 'translation';
import {getOptimizeProfile} from 'config';

import './EditOwnerModal.scss';

export default function EditOwnerModal({initialOwner, onClose, onConfirm}) {
  const [selectedUser, setSelectedUser] = useState(
    initialOwner?.id ? {id: 'USER:' + initialOwner.id, identity: initialOwner} : null
  );
  const [deleting, setDeleting] = useState();
  const [optimizeProfile, setOptimizeProfile] = useState();

  useEffect(() => {
    (async () => {
      setOptimizeProfile(await getOptimizeProfile());
    })();
  }, []);

  return (
    <Modal open onClose={onClose} className="EditOwnerModal">
      <Modal.Header>{t('processes.addProcessOwner')}</Modal.Header>
      <Modal.Content>
        <p className="ownerInfo">{t('processes.ownerInfo')}</p>
        <Labeled label={t('common.user.label-plural')}>
          <UserTypeahead
            users={selectedUser ? [selectedUser] : []}
            onChange={(users) => setSelectedUser(users[users.length - 1])}
            excludeGroups
            optionsOnly={optimizeProfile === 'cloud'}
          />
        </Labeled>
        <Deleter
          type="owner"
          entity={deleting}
          onClose={() => {
            setDeleting();
          }}
          getName={({name}) => name}
          deleteEntity={async () => {
            await onConfirm(null);
          }}
          deleteText={t('common.removeEntity', {entity: t('processes.owner')})}
          descriptionText={t('processes.ownerRemoveWarning', {owner: initialOwner?.name || ''})}
          deleteButtonText={t('common.removeEntity', {entity: t('processes.owner')})}
          isReversableAction
        />
      </Modal.Content>
      <Modal.Actions>
        {initialOwner?.id && (
          <Button link className="deleteButton" onClick={() => setDeleting(initialOwner)}>
            {t('common.removeEntity', {entity: t('processes.owner')})}
          </Button>
        )}

        <Button main className="close" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button
          main
          primary
          disabled={!selectedUser || selectedUser?.identity.id === initialOwner?.id}
          className="confirm"
          onClick={() => {
            onConfirm(selectedUser.identity.id);
          }}
        >
          {t('common.add')}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}
