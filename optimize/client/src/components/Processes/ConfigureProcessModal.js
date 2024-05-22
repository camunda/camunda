/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';
import {ActionableNotification, Button, Stack, Toggle, Tooltip} from '@carbon/react';
import {Information} from '@carbon/icons-react';

import {Modal, UserTypeahead} from 'components';
import {t} from 'translation';
import {getOptimizeProfile, isEmailEnabled} from 'config';
import {useDocs} from 'hooks';

import './ConfigureProcessModal.scss';

export function ConfigureProcessModal({
  initialConfig: {
    owner,
    digest: {enabled},
  },
  onClose,
  onConfirm,
}) {
  const [selectedUser, setSelectedUser] = useState(
    owner?.id ? {id: 'USER:' + owner.id, identity: {...owner, type: 'user'}} : null
  );
  const [digestEnabled, setDigestEnabled] = useState(enabled);
  const [optimizeProfile, setOptimizeProfile] = useState();
  const [emailEnabled, setEmailEnabled] = useState();
  const {generateDocsLink} = useDocs();

  const noChangesHappened =
    digestEnabled === enabled &&
    ((!selectedUser?.identity.id && !owner?.id) || selectedUser?.identity.id === owner?.id);

  useEffect(() => {
    (async () => {
      setOptimizeProfile(await getOptimizeProfile());
      setEmailEnabled(await isEmailEnabled());
    })();
  }, []);

  return (
    <Modal open onClose={onClose} className="ConfigureProcessModal" isOverflowVisible>
      <Modal.Header title={t('processes.configureProcess')} />
      <Modal.Content>
        <Stack gap={6}>
          {!emailEnabled && (
            <ActionableNotification
              kind="warning"
              subtitle={t('alert.emailWarning', {
                docsLink: generateDocsLink(
                  'self-managed/optimize-deployment/configuration/system-configuration/#email'
                ),
              })}
              className="emailWarning"
            />
          )}
          <UserTypeahead
            titleText={
              <span className="infoContainer">
                {t('processes.processOwner')}
                <Tooltip label={t('processes.ownerInfo')}>
                  <Information />
                </Tooltip>
              </span>
            }
            key={selectedUser?.id}
            users={selectedUser ? [selectedUser] : []}
            onChange={(users) => {
              const newSelection = users[users.length - 1];
              setSelectedUser(newSelection);
              if (!newSelection) {
                setDigestEnabled(false);
              }
            }}
            excludeGroups
            optionsOnly={optimizeProfile === 'cloud'}
          />
          <div className="infoContainer">
            <Toggle
              id="digestSwitch"
              disabled={!selectedUser}
              labelText={t('processes.emailDigest')}
              size="sm"
              hideLabel
              toggled={digestEnabled}
              onToggle={(checked) => {
                if (checked && selectedUser) {
                  setDigestEnabled(true);
                } else {
                  setDigestEnabled(false);
                }
              }}
            />
            <Tooltip label={t('processes.digestInfo')}>
              <Information />
            </Tooltip>
          </div>
        </Stack>
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" className="close" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button
          disabled={noChangesHappened}
          className="confirm"
          onClick={() => {
            const ownerId = selectedUser?.identity.id || null;
            onConfirm(
              {
                ownerId,
                processDigest: {enabled: digestEnabled},
              },
              emailEnabled,
              selectedUser?.identity.name
            );
          }}
        >
          {t('common.save')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

export default ConfigureProcessModal;
