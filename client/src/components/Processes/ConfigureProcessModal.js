/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useState} from 'react';
import equal from 'fast-deep-equal';

import {
  Button,
  Form,
  Icon,
  Input,
  Labeled,
  MessageBox,
  Modal,
  Select,
  Switch,
  Tooltip,
  UserTypeahead,
} from 'components';
import {t} from 'translation';
import {getOptimizeProfile, isEmailEnabled} from 'config';
import {withDocs} from 'HOC';

import './ConfigureProcessModal.scss';

export function ConfigureProcessModal({
  initialConfig: {
    owner,
    digest: {enabled, checkInterval},
  },
  onClose,
  onConfirm,
  docsLink,
}) {
  const [selectedUser, setSelectedUser] = useState(
    owner?.id ? {id: 'USER:' + owner.id, identity: owner} : null
  );
  const [digestEnabled, setDigestEnabled] = useState(enabled);
  const [digestInterval, setDigestInterval] = useState(checkInterval);
  const [optimizeProfile, setOptimizeProfile] = useState();
  const [emailEnabled, setEmailEnabled] = useState();

  const noChangesHappened =
    digestEnabled === enabled &&
    equal(digestInterval, checkInterval) &&
    selectedUser?.identity.id === owner.id;

  useEffect(() => {
    (async () => {
      setOptimizeProfile(await getOptimizeProfile());
      setEmailEnabled(await isEmailEnabled());
    })();
  }, []);

  return (
    <Modal open onClose={onClose} className="ConfigureProcessModal">
      <Modal.Header>{t('processes.configureProcess')}</Modal.Header>
      <Modal.Content>
        {!emailEnabled && (
          <MessageBox
            type="warning"
            dangerouslySetInnerHTML={{
              __html: t('alert.emailWarning', {
                docsLink: docsLink + 'technical-guide/setup/configuration/#email',
              }),
            }}
          />
        )}
        <Labeled
          className="ownerConfig"
          label={
            <>
              {t('processes.processOwner')}{' '}
              <Tooltip align="center" content={t('processes.ownerInfo')}>
                <Icon type="info" />
              </Tooltip>
            </>
          }
        >
          <UserTypeahead
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
            persistMenu={false}
          />
        </Labeled>
        <fieldset className="digestConfig" disabled={!selectedUser}>
          <legend>
            <Switch
              label={t('processes.emailDigest')}
              checked={digestEnabled}
              onChange={({target}) => {
                if (target.checked && selectedUser) {
                  setDigestEnabled(true);
                } else {
                  setDigestEnabled(false);
                }
              }}
            />
          </legend>
          <Form.Group noSpacing>
            <Labeled label={t('alert.form.reminderFrequency')}>
              <Form.InputGroup>
                <Input
                  value={digestInterval.value}
                  onChange={({target: {value}}) =>
                    setDigestInterval((interval) => ({...interval, value}))
                  }
                  maxLength="8"
                />
                <Select
                  value={digestInterval.unit}
                  onChange={(unit) => setDigestInterval((interval) => ({...interval, unit}))}
                >
                  <Select.Option value="minutes">
                    {t('common.unit.minute.label-plural')}
                  </Select.Option>
                  <Select.Option value="hours">{t('common.unit.hour.label-plural')}</Select.Option>
                  <Select.Option value="days">{t('common.unit.day.label-plural')}</Select.Option>
                  <Select.Option value="weeks">{t('common.unit.week.label-plural')}</Select.Option>
                  <Select.Option value="months">
                    {t('common.unit.month.label-plural')}
                  </Select.Option>
                </Select>
              </Form.InputGroup>
            </Labeled>
          </Form.Group>
        </fieldset>
      </Modal.Content>
      <Modal.Actions>
        <Button main className="close" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button
          main
          primary
          disabled={noChangesHappened}
          className="confirm"
          onClick={() => {
            const ownerId = selectedUser?.identity.id || null;
            onConfirm({
              ownerId,
              processDigest: {enabled: digestEnabled, checkInterval: digestInterval},
            });
          }}
        >
          {t('common.save')}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}

export default withDocs(ConfigureProcessModal);
