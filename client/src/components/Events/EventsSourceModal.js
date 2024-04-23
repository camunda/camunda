/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useCallback, useEffect, useState} from 'react';
import update from 'immutability-helper';
import {
  ActionableNotification,
  Button,
  ComboBox,
  Form,
  FormGroup,
  RadioButton,
  RadioButtonGroup,
  Stack,
} from '@carbon/react';

import {Modal, DefinitionSelection, DocsLink, Tabs} from 'components';
import {t} from 'translation';
import {loadVariables as loadVariablesService} from 'services';
import {showError} from 'notifications';
import {useErrorHandling} from 'hooks';

import ExternalSourceSelection from './ExternalSourceSelection';
import {loadEvents} from './service';

import './EventsSourceModal.scss';

const allExternalGroups = [
  {type: 'external', configuration: {includeAllGroups: true, group: null}},
];

const defaultSource = {
  processDefinitionKey: '',
  processDefinitionName: '',
  versions: [],
  tenants: [],
  eventScope: ['process_instance'],
  tracedByBusinessKey: false,
  traceVariable: null,
};

export default function EventsSourceModal({
  initialSource,
  existingSources,
  autoGenerate,
  onConfirm,
  onClose,
}) {
  const [source, setSource] = useState(initialSource?.configuration || defaultSource);
  const [variables, setVariables] = useState(null);
  const [type, setType] = useState('camunda');
  const [externalExist, setExternalExist] = useState(false);
  const [externalSources, setExternalSources] = useState([]);
  const {mightFail} = useErrorHandling();
  const {processDefinitionKey, versions, tenants, tracedByBusinessKey, traceVariable, eventScope} =
    source;

  function updateSources() {
    if (type === 'external') {
      const camundaSources = existingSources.filter((src) => src.type !== 'external');
      const existingExternalGroups = existingSources.filter(
        (src) => src.type === 'external' && !src.configuration.includeAllGroups
      );
      const newExternalSources =
        autoGenerate || includeAllGroups(externalSources)
          ? allExternalGroups
          : externalSources.concat(existingExternalGroups);

      const updatedSources = camundaSources.concat(newExternalSources);
      onConfirm(updatedSources, camundaSources.length < existingSources.length);
    } else {
      let updatedSources;
      const newSource = {
        type: 'camunda',
        configuration: {
          ...source,
          traceVariable: source.tracedByBusinessKey ? null : source.traceVariable,
        },
      };

      if (isEditing()) {
        const sourceIndex = existingSources.findIndex(
          ({configuration: {processDefinitionKey}}) =>
            processDefinitionKey === source.processDefinitionKey
        );

        updatedSources = update(existingSources, {[sourceIndex]: {$set: newSource}});
      } else {
        updatedSources = update(existingSources, {$push: [newSource]});
      }
      onConfirm(updatedSources, isEditing());
    }
  }

  const isEditing = useCallback(() => !!initialSource?.configuration, [initialSource]);

  function alreadyExists() {
    return existingSources.some(
      (existingSource) =>
        existingSource.configuration.processDefinitionKey === source.processDefinitionKey
    );
  }

  function isValid() {
    if (type === 'external') {
      return externalExist && (externalSources.length > 0 || autoGenerate);
    } else {
      const {processDefinitionKey, tracedByBusinessKey, traceVariable} = source;
      return (
        processDefinitionKey &&
        (tracedByBusinessKey || traceVariable) &&
        (isEditing() || !alreadyExists())
      );
    }
  }

  const loadVariables = useCallback(
    (processDefinitionKey, processDefinitionVersions, tenantIds) => {
      if (processDefinitionKey && processDefinitionVersions && tenantIds) {
        mightFail(
          loadVariablesService({
            processesToQuery: [
              {
                processDefinitionKey,
                processDefinitionVersions,
                tenantIds,
              },
            ],
            filter: [],
          }),
          setVariables,
          showError
        );
      }
    },
    [mightFail]
  );

  function updateSource(key, value) {
    setSource(update(source, {[key]: {$set: value}}));
  }

  useEffect(() => {
    if (isEditing()) {
      const {processDefinitionKey, versions, tenants} = initialSource.configuration;
      loadVariables(processDefinitionKey, versions, tenants);
    }

    mightFail(
      loadEvents({eventSources: allExternalGroups}),
      (events) => setExternalExist(!!events.length),
      showError
    );
  }, [initialSource, mightFail, isEditing, loadVariables]);

  return (
    <Modal open onClose={onClose} className="EventsSourceModal" isOverflowVisible>
      <Modal.Header>
        {isEditing() ? t('events.sources.editSource') : t('events.sources.addEvents')}
      </Modal.Header>
      <Modal.Content>
        <Tabs value={type} onChange={setType} showButtons={!isEditing()}>
          <Tabs.Tab value="camunda" title={t('events.sources.camundaEvents')}>
            <Stack gap={6}>
              <DefinitionSelection
                type="process"
                definitionKey={processDefinitionKey}
                versions={versions}
                tenants={tenants}
                disableDefinition={isEditing()}
                expanded
                camundaEventImportedOnly
                onChange={({key, name, versions, tenantIds}) => {
                  loadVariables(key, versions, tenantIds);
                  setSource(
                    update(source, {
                      $merge: {
                        processDefinitionName: name,
                        processDefinitionKey: key,
                        versions,
                        tenants: tenantIds,
                        traceVariable: undefined,
                      },
                    })
                  );
                }}
                invalid={!isEditing() && alreadyExists()}
                invalidText={t('events.sources.alreadyExists')}
              />
              <Form className="sourceOptions">
                <Stack gap={6}>
                  <FormGroup legendText={t('events.sources.defineTrace')}>
                    <Stack gap={6}>
                      <RadioButtonGroup
                        name="variable-or-business-key-selector"
                        orientation="vertical"
                      >
                        <RadioButton
                          value="true"
                          checked={tracedByBusinessKey}
                          onClick={() => updateSource('tracedByBusinessKey', true)}
                          labelText={t('events.sources.byKey')}
                        />
                        <RadioButton
                          value="false"
                          checked={!tracedByBusinessKey}
                          onClick={() => updateSource('tracedByBusinessKey', false)}
                          labelText={t('events.sources.byVariable')}
                        />
                      </RadioButtonGroup>
                      <ComboBox
                        className="variablesSelector"
                        id="variable-selector"
                        items={variables || []}
                        itemToString={(item) => item?.label || item?.name}
                        selectedItem={
                          (variables &&
                            variables.find((variable) => variable.name === traceVariable)) ||
                          null
                        }
                        disabled={!processDefinitionKey || tracedByBusinessKey}
                        placeholder={t('common.filter.variableModal.inputPlaceholder')}
                        onChange={({selectedItem: traceVariable}) =>
                          updateSource('traceVariable', traceVariable?.name || traceVariable?.label)
                        }
                        helperText={getDisabledMessage(
                          tracedByBusinessKey,
                          variables,
                          processDefinitionKey
                        )}
                      />
                    </Stack>
                  </FormGroup>
                  {!isEditing() && (
                    <FormGroup
                      legendText={
                        <div className="displayHeader">
                          <h4>
                            {autoGenerate
                              ? t('events.sources.generatedEvents')
                              : t('events.sources.display')}
                          </h4>
                          <DocsLink location="components/userguide/additional-features/event-based-processes/#camunda-events">
                            {t('events.sources.learnMore')}
                          </DocsLink>
                        </div>
                      }
                    >
                      <RadioButtonGroup name="event-type-selector" orientation="vertical">
                        <RadioButton
                          value="process_instance"
                          labelText={t('events.sources.startAndEnd')}
                          onClick={() => updateSource('eventScope', ['process_instance'])}
                          checked={eventScope.includes('process_instance')}
                        />
                        <RadioButton
                          value="start_end"
                          onClick={() => updateSource('eventScope', ['start_end'])}
                          checked={eventScope.includes('start_end')}
                          labelText={t('events.sources.flownodeEvents')}
                        />
                        {!autoGenerate ? (
                          <RadioButton
                            value="all"
                            labelText={t('events.sources.allEvents')}
                            onClick={() => updateSource('eventScope', ['all'])}
                            checked={eventScope.includes('all')}
                          />
                        ) : (
                          <></>
                        )}
                      </RadioButtonGroup>
                    </FormGroup>
                  )}
                  {isEditing() && (
                    <ActionableNotification
                      kind="warning"
                      className="editingWarning"
                      hideCloseButton
                    >
                      {t('events.sources.definitionChangeWarning')}{' '}
                      <DocsLink location="components/userguide/additional-features/event-based-processes/#camunda-events">
                        {t('events.sources.learnMore')}
                      </DocsLink>
                    </ActionableNotification>
                  )}
                </Stack>
              </Form>
            </Stack>
          </Tabs.Tab>
          <Tabs.Tab value="external" title={t('events.sources.externalEvents')}>
            {!autoGenerate && (
              <ExternalSourceSelection
                empty={!externalExist}
                existingExternalSources={existingSources.filter((src) => src.type === 'external')}
                externalSources={externalSources}
                onChange={setExternalSources}
              />
            )}
            {autoGenerate && (
              <p className="addExternalInfo">{t('events.sources.addExternalInfo')}</p>
            )}
          </Tabs.Tab>
        </Tabs>
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" className="close" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button disabled={!isValid()} className="confirm" onClick={updateSources}>
          {isEditing() ? t('common.update') : t('common.add')}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

function getDisabledMessage(tracedByBusinessKey, variables, processDefinitionKey) {
  if (tracedByBusinessKey) {
    return;
  }

  if (variables && !variables.length) {
    return t('common.filter.variableModal.noVariables');
  }

  if (!processDefinitionKey) {
    return t('events.sources.selectProcess');
  }

  return;
}

function includeAllGroups(sources) {
  return sources.some((src) => src.configuration.includeAllGroups);
}
