/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useState} from 'react';
import {Form, LabeledInput, Modal, MessageBox, Button} from 'components';
import {getOptimizeVersion} from 'config';
import {t} from 'translation';

export default function VisibleEventsModal({initialScope, onClose, onConfirm}) {
  const [scope, setScope] = useState(initialScope);
  const [optimizeVersion, setOptimizeVersion] = useState();

  useEffect(() => {
    (async () => {
      const version = (await getOptimizeVersion()).split('.');
      version.length = 2;
      setOptimizeVersion(version.join('.'));
    })();
  }, []);

  const docsLink = `https://docs.camunda.org/optimize/${optimizeVersion}/user-guide/event-based-processes/#camunda-events`;

  const toggleScopeItem = (item) => {
    if (scope.includes(item)) {
      return setScope(scope.filter((i) => i !== item));
    }
    return setScope([...scope, item]);
  };

  const updateSource = () => scope.length > 0 && onConfirm(scope);

  return (
    <Modal open onClose={onClose} onConfirm={updateSource}>
      <Modal.Header>{t('events.sources.editScope')}</Modal.Header>
      <Modal.Content>
        <Form description={t('events.sources.eventListTip')}>
          <Form.Group>
            <LabeledInput
              label={t('events.sources.startAndEnd')}
              checked={scope.includes('process_instance')}
              onChange={() => toggleScopeItem('process_instance')}
              type="checkbox"
            />
            <LabeledInput
              checked={scope.includes('start_end')}
              onChange={() => toggleScopeItem('start_end')}
              label={t('events.sources.flownodeEvents')}
              type="checkbox"
            />
            <LabeledInput
              label={t('events.sources.allEvents')}
              checked={scope.includes('all')}
              onChange={() => toggleScopeItem('all')}
              type="checkbox"
            />
          </Form.Group>
          <MessageBox type="warning">
            {t('events.sources.eventListChangeWarning')}{' '}
            <a href={docsLink} target="_blank" rel="noopener noreferrer">
              {t('events.sources.learnMore')}
            </a>
          </MessageBox>
        </Form>
      </Modal.Content>
      <Modal.Actions>
        <Button main className="close" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button disabled={!scope.length} main primary className="confirm" onClick={updateSource}>
          {t('common.update')}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}
