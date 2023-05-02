/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import update from 'immutability-helper';
import {Button} from '@carbon/react';

import {
  CarbonModal as Modal,
  DefinitionSelection,
  LabeledInput,
  Typeahead,
  Form,
  Message,
  MessageBox,
  DocsLink,
  Tabs,
} from 'components';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {loadVariables} from 'services';
import {showError} from 'notifications';

import ExternalSource from './ExternalSource';
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

export default withErrorHandling(
  class EventsSourceModal extends React.Component {
    state = {
      source: this.props.initialSource?.configuration || defaultSource,
      variables: null,
      type: 'camunda',
      externalExist: false,
      externalSources: [],
    };

    componentDidMount = async () => {
      if (this.isEditing()) {
        const {processDefinitionKey, versions, tenants} = this.props.initialSource.configuration;
        this.loadVariables(processDefinitionKey, versions, tenants);
      }

      this.props.mightFail(
        loadEvents({eventSources: allExternalGroups}),
        (events) => this.setState({externalExist: !!events.length}),
        showError
      );
    };

    updateSources = () => {
      const {existingSources, autoGenerate} = this.props;
      const {source, type, externalSources} = this.state;
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
        this.props.onConfirm(updatedSources, camundaSources.length < existingSources.length);
      } else {
        let updatedSources;
        const newSource = {
          type: 'camunda',
          configuration: {
            ...source,
            traceVariable: source.tracedByBusinessKey ? null : source.traceVariable,
          },
        };

        if (this.isEditing()) {
          const sourceIndex = existingSources.findIndex(
            ({configuration: {processDefinitionKey}}) =>
              processDefinitionKey === source.processDefinitionKey
          );

          updatedSources = update(existingSources, {[sourceIndex]: {$set: newSource}});
        } else {
          updatedSources = update(existingSources, {$push: [newSource]});
        }
        this.props.onConfirm(updatedSources, this.isEditing());
      }
    };

    isEditing = () => !!this.props.initialSource?.configuration;

    alreadyExists = () =>
      this.props.existingSources.some(
        (source) => source.processDefinitionKey === this.state.source.processDefinitionKey
      );

    isValid = () => {
      const {source, type, externalExist, externalSources} = this.state;
      if (type === 'external') {
        return externalExist && (externalSources.length > 0 || this.props.autoGenerate);
      } else {
        const {processDefinitionKey, tracedByBusinessKey, traceVariable} = source;
        return (
          processDefinitionKey &&
          (tracedByBusinessKey || traceVariable) &&
          (this.isEditing() || !this.alreadyExists())
        );
      }
    };

    loadVariables = (processDefinitionKey, processDefinitionVersions, tenantIds) => {
      if (processDefinitionKey && processDefinitionVersions && tenantIds) {
        this.props.mightFail(
          loadVariables([
            {
              processDefinitionKey,
              processDefinitionVersions,
              tenantIds,
            },
          ]),
          (variables) => this.setState({variables}),
          showError
        );
      }
    };

    updateSource = (key, value) => {
      this.setState({source: update(this.state.source, {[key]: {$set: value}})});
    };

    render() {
      const {onClose, autoGenerate} = this.props;
      const {source, variables, type, externalExist, externalSources} = this.state;
      const {
        processDefinitionKey,
        versions,
        tenants,
        tracedByBusinessKey,
        traceVariable,
        eventScope,
      } = source;

      return (
        <Modal open onClose={onClose} className="EventsSourceModal" isOverflowVisible>
          <Modal.Header>
            {this.isEditing() ? t('events.sources.editSource') : t('events.sources.addEvents')}
          </Modal.Header>
          <Modal.Content>
            <Tabs
              value={type}
              onChange={(type) => this.setState({type})}
              showButtons={!this.isEditing()}
            >
              <Tabs.Tab value="camunda" title={t('events.sources.camundaEvents')}>
                <DefinitionSelection
                  type="process"
                  definitionKey={processDefinitionKey}
                  versions={versions}
                  tenants={tenants}
                  disableDefinition={this.isEditing()}
                  expanded
                  camundaEventImportedOnly
                  onChange={({key, name, versions, tenantIds}) => {
                    this.loadVariables(key, versions, tenantIds);
                    this.setState({
                      source: update(this.state.source, {
                        $merge: {
                          processDefinitionName: name,
                          processDefinitionKey: key,
                          versions,
                          tenants: tenantIds,
                          traceVariable: undefined,
                        },
                      }),
                    });
                  }}
                />
                {!this.isEditing() && this.alreadyExists() && (
                  <Message error>{t('events.sources.alreadyExists')}</Message>
                )}
                <Form className="sourceOptions">
                  <Form.Group>
                    <h4>{t('events.sources.defineTrace')}</h4>
                    <LabeledInput
                      checked={!tracedByBusinessKey}
                      onChange={() => this.updateSource('tracedByBusinessKey', false)}
                      type="radio"
                      label={t('events.sources.byVariable')}
                    />
                    <Typeahead
                      value={variables && traceVariable}
                      noValuesMessage={getDisabledMessage(tracedByBusinessKey, variables)}
                      disabled={tracedByBusinessKey}
                      placeholder={t('common.filter.variableModal.inputPlaceholder')}
                      onChange={(traceVariable) =>
                        this.updateSource('traceVariable', traceVariable)
                      }
                    >
                      {variables &&
                        variables.map(({name, label}) => (
                          <Typeahead.Option key={name} value={name}>
                            {label || name}
                          </Typeahead.Option>
                        ))}
                    </Typeahead>
                    <LabeledInput
                      checked={tracedByBusinessKey}
                      onChange={() => this.updateSource('tracedByBusinessKey', true)}
                      type="radio"
                      label={t('events.sources.byKey')}
                    />
                  </Form.Group>
                  {!this.isEditing() && (
                    <Form.Group>
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
                      <LabeledInput
                        label={t('events.sources.startAndEnd')}
                        onChange={() => this.updateSource('eventScope', ['process_instance'])}
                        checked={eventScope.includes('process_instance')}
                        type="radio"
                      />
                      <LabeledInput
                        onChange={() => this.updateSource('eventScope', ['start_end'])}
                        checked={eventScope.includes('start_end')}
                        label={t('events.sources.flownodeEvents')}
                        type="radio"
                      />
                      {!autoGenerate && (
                        <LabeledInput
                          label={t('events.sources.allEvents')}
                          onChange={() => this.updateSource('eventScope', ['all'])}
                          checked={eventScope.includes('all')}
                          type="radio"
                        />
                      )}
                    </Form.Group>
                  )}
                  {this.isEditing() && (
                    <MessageBox type="warning">
                      {t('events.sources.definitionChangeWarning')}{' '}
                      <DocsLink location="components/userguide/additional-features/event-based-processes/#camunda-events">
                        {t('events.sources.learnMore')}
                      </DocsLink>
                    </MessageBox>
                  )}
                </Form>
              </Tabs.Tab>
              <Tabs.Tab value="external" title={t('events.sources.externalEvents')}>
                {!autoGenerate && (
                  <ExternalSource
                    empty={!externalExist}
                    existingExternalSources={this.props.existingSources.filter(
                      (src) => src.type === 'external'
                    )}
                    externalSources={externalSources}
                    onChange={(externalSources) => this.setState({externalSources})}
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
            <Button disabled={!this.isValid()} className="confirm" onClick={this.updateSources}>
              {this.isEditing() ? t('common.update') : t('common.add')}
            </Button>
          </Modal.Footer>
        </Modal>
      );
    }
  }
);

function getDisabledMessage(tracedByBusinessKey, variables) {
  if (tracedByBusinessKey) {
    return t('common.none');
  }

  if (variables && !variables.length) {
    return t('common.filter.variableModal.noVariables');
  }

  return t('events.sources.selectProcess');
}

function includeAllGroups(sources) {
  return sources.some((src) => src.configuration.includeAllGroups);
}
