/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';
import {Redirect} from 'react-router';
import {Button, Modal, EntityList, Icon} from 'components';
import {t} from 'translation';
import EventsSourceModal from './EventsSourceModal';
import {createProcess} from './service';
import {getOptimizeVersion} from 'config';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import './GenerationModal.scss';

export function GenerationModal({onClose, mightFail}) {
  const [sources, setSources] = useState([]);
  const [openEventsSourceModal, setOpenEventsSourceModal] = useState(false);
  const [version, setVersion] = useState();
  const [redirect, setRedirect] = useState();

  useEffect(() => {
    (async () => {
      const version = (await getOptimizeVersion()).split('.');
      version.length = 2;
      setVersion(version.join('.'));
    })();
  }, []);

  const removeSource = (target) => setSources(sources.filter((src) => src !== target));

  const onConfirm = async () =>
    mightFail(createProcess({eventSources: sources, autogenerate: true}), setRedirect, showError);

  if (redirect) {
    return <Redirect to={`/eventBasedProcess/${redirect}/generated`} />;
  }

  const docsLink = `https://docs.camunda.org/optimize/${version}/user-guide/event-based-processes#autogenerate`;
  return (
    <Modal
      className="GenerationModal"
      open
      onClose={onClose}
      onConfirm={sources.length > 0 && onConfirm}
    >
      <Modal.Header>{t('events.autogenerate')}</Modal.Header>
      <Modal.Content>
        <p className="description">
          {t('events.generationInfo')}{' '}
          <a href={docsLink} target="_blank" rel="noopener noreferrer">
            {t('events.sources.learnMore')}
          </a>
        </p>
        <EntityList
          embedded
          action={
            <Button onClick={() => setOpenEventsSourceModal(true)}>
              <Icon type="plus" />
              {t('events.sources.add')}
            </Button>
          }
          name={t('events.addedSources')}
          empty={t('home.sources.notCreated')}
          data={sources.map((source) => {
            const {processDefinitionKey, processDefinitionName, type} = source;
            const actions = [
              {
                icon: 'delete',
                text: t('common.remove'),
                action: () => removeSource(source),
              },
            ];

            if (type === 'external') {
              return {
                icon: 'data-source',
                type: t('events.sources.externalEvents'),
                name: t('events.sources.allExternal'),
                actions,
              };
            } else {
              return {
                icon: 'camunda-source',
                type: t('events.sources.camundaProcess'),
                name: processDefinitionName || processDefinitionKey,
                actions,
              };
            }
          })}
        />
        {openEventsSourceModal && (
          <EventsSourceModal
            autoGenerate
            existingSources={sources}
            onConfirm={(sources) => {
              setSources(sources);
              setOpenEventsSourceModal(false);
            }}
            onClose={() => setOpenEventsSourceModal(false)}
          />
        )}
      </Modal.Content>
      <Modal.Actions>
        <Button main onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button main primary disabled={!sources.length} onClick={onConfirm}>
          {t('events.generate')}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}

export default withErrorHandling(GenerationModal);
