/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import update from 'immutability-helper';

import {
  Modal,
  Button,
  DefinitionSelection,
  LabeledInput,
  Typeahead,
  Form,
  Message,
  ButtonGroup
} from 'components';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {loadVariables} from 'services';

import './EventsSourceModal.scss';
import {showError} from 'notifications';
import ExternalSource from './ExternalSource';
import {loadEvents} from './service';

export default withErrorHandling(
  class EventsSourceModal extends React.Component {
    state = {
      source: {
        processDefinitionKey: '',
        processDefinitionName: '',
        versions: [],
        tenants: [],
        eventScope: 'start_end',
        tracedByBusinessKey: false,
        traceVariable: null
      },
      variables: null,
      type: 'camunda',
      externalExist: false
    };

    componentDidMount = () => {
      if (this.isEditing()) {
        this.setState({source: this.props.initialSource});
        const {processDefinitionKey, versions, tenants} = this.props.initialSource;
        this.loadVariables(processDefinitionKey, versions, tenants);
      }

      this.props.mightFail(
        loadEvents({eventSources: [{type: 'external'}]}),
        events => this.setState({externalExist: !!events.length}),
        showError
      );
    };

    updateSources = () => {
      const {existingSources} = this.props;
      const {source, type} = this.state;
      let updatedSources;
      if (type === 'external') {
        updatedSources = update(existingSources, {$push: [{type: 'external'}]});
      } else {
        const newSource = {
          ...source,
          traceVariable: source.tracedByBusinessKey ? null : source.traceVariable
        };

        if (this.isEditing()) {
          const sourceIndex = existingSources.findIndex(
            ({processDefinitionKey}) => processDefinitionKey === source.processDefinitionKey
          );

          updatedSources = update(existingSources, {[sourceIndex]: {$set: newSource}});
        } else {
          updatedSources = update(existingSources, {$push: [newSource]});
        }
      }
      this.props.onConfirm(updatedSources);
    };

    isEditing = () => this.props.initialSource.processDefinitionKey;

    alreadyExists = () =>
      this.props.existingSources.some(
        source => source.processDefinitionKey === this.state.source.processDefinitionKey
      );

    isValid = () => {
      const {source, type, externalExist} = this.state;
      if (type === 'external') {
        return externalExist;
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
          loadVariables({
            processDefinitionKey,
            processDefinitionVersions,
            tenantIds
          }),
          variables => this.setState({variables}),
          showError
        );
      }
    };

    updateSource = (key, value) => {
      this.setState({source: update(this.state.source, {[key]: {$set: value}})});
    };

    render() {
      const {onClose, existingSources} = this.props;
      const {source, variables, type, externalExist} = this.state;
      const {
        processDefinitionKey,
        versions,
        tenants,
        eventScope,
        tracedByBusinessKey,
        traceVariable
      } = source;

      const externalAlreadyAdded = existingSources.some(src => src.type === 'external');

      return (
        <Modal open onClose={onClose} onConfirm={this.updateSources} className="EventsSourceModal">
          <Modal.Header>
            {this.isEditing() ? t('events.sources.editEvents') : t('events.sources.addEvents')}
          </Modal.Header>
          <Modal.Content>
            <ButtonGroup>
              <Button active={type === 'camunda'} onClick={() => this.setState({type: 'camunda'})}>
                {t('events.sources.camundaEvents')}
              </Button>
              <Button
                active={type === 'external'}
                onClick={() => this.setState({type: 'external'})}
                disabled={this.isEditing() || externalAlreadyAdded}
              >
                {t('events.sources.externalEvents')}
              </Button>
            </ButtonGroup>
            {type === 'camunda' && (
              <>
                <DefinitionSelection
                  type="process"
                  definitionKey={processDefinitionKey}
                  versions={versions}
                  tenants={tenants}
                  disableDefinition={this.isEditing()}
                  expanded
                  onChange={({key, name, versions, tenantIds}) => {
                    this.loadVariables(key, versions, tenantIds);
                    this.setState({
                      source: update(this.state.source, {
                        $merge: {
                          processDefinitionName: name,
                          processDefinitionKey: key,
                          versions,
                          tenants: tenantIds,
                          traceVariable: null
                        }
                      })
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
                      onChange={traceVariable => this.updateSource('traceVariable', traceVariable)}
                    >
                      {variables &&
                        variables.map(({name}) => (
                          <Typeahead.Option key={name} value={name}>
                            {name}
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
                  <Form.Group>
                    <h4>{t('events.sources.display')}</h4>
                    <LabeledInput
                      checked={eventScope === 'start_end'}
                      onChange={() => this.updateSource('eventScope', 'start_end')}
                      label={t('events.sources.startAndEnd')}
                      type="radio"
                    />
                    <LabeledInput
                      checked={eventScope === 'process_instance'}
                      onChange={() => this.updateSource('eventScope', 'process_instance')}
                      label={t('events.sources.flownodeEvents')}
                      type="radio"
                    />
                    <LabeledInput
                      label={t('events.sources.allEvents')}
                      checked={eventScope === 'all'}
                      onChange={() => this.updateSource('eventScope', 'all')}
                      type="radio"
                    />
                  </Form.Group>
                </Form>
              </>
            )}
            {type === 'external' && <ExternalSource empty={!externalExist} />}
          </Modal.Content>
          <Modal.Actions>
            <Button className="close" onClick={onClose}>
              {t('common.cancel')}
            </Button>
            <Button
              disabled={!this.isValid()}
              variant="primary"
              color="blue"
              className="confirm"
              onClick={this.updateSources}
            >
              {this.isEditing() ? t('common.update') : t('common.add')}
            </Button>
          </Modal.Actions>
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
