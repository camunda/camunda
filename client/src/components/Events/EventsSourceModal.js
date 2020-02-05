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
  Message
} from 'components';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {loadVariables} from 'services';

import './EventsSourceModal.scss';

export default withErrorHandling(
  class EventsSourceModal extends React.Component {
    state = {
      processDefinitionKey: '',
      processDefinitionName: '',
      versions: [],
      tenants: [],
      eventScope: 'start_end',
      tracedByBusinessKey: false,
      variables: null,
      traceVariable: null
    };

    componentDidMount = () => {
      if (this.isEditing()) {
        this.setState(this.props.source, this.loadVariables);
      }
    };

    updateSources = () => {
      const {existingSources} = this.props;
      let updatedSources;
      const {
        processDefinitionKey,
        processDefinitionName,
        versions,
        tenants,
        eventScope,
        tracedByBusinessKey,
        traceVariable
      } = this.state;

      const newSource = {
        processDefinitionKey,
        processDefinitionName,
        versions,
        tenants,
        tracedByBusinessKey,
        traceVariable: tracedByBusinessKey ? null : traceVariable,
        eventScope
      };

      if (this.isEditing()) {
        const sourceIndex = existingSources.findIndex(
          source => source.processDefinitionKey === processDefinitionKey
        );

        updatedSources = update(existingSources, {[sourceIndex]: {$set: newSource}});
      } else {
        updatedSources = update(existingSources, {$push: [newSource]});
      }

      this.props.onConfirm(updatedSources);
    };

    isEditing = () => this.props.source.processDefinitionKey;

    alreadyExists = () =>
      this.props.existingSources.some(
        source => source.processDefinitionKey === this.state.processDefinitionKey
      );

    isValid = () => {
      const {processDefinitionKey, tracedByBusinessKey, traceVariable} = this.state;
      return (
        processDefinitionKey &&
        (tracedByBusinessKey || traceVariable) &&
        (this.isEditing() || !this.alreadyExists())
      );
    };

    loadVariables = async () => {
      const {processDefinitionKey, versions, tenants} = this.state;
      if (processDefinitionKey && versions && tenants) {
        this.props.mightFail(
          loadVariables({
            processDefinitionKey,
            processDefinitionVersions: versions,
            tenantIds: tenants
          }),
          variables => this.setState({variables})
        );
      }
    };

    render() {
      const {onClose} = this.props;
      const {
        processDefinitionKey,
        versions,
        tenants,
        eventScope,
        tracedByBusinessKey,
        variables,
        traceVariable
      } = this.state;

      return (
        <Modal
          open={true}
          onClose={onClose}
          onConfirm={this.updateSources}
          className="EventsSourceModal"
        >
          <Modal.Header>
            {this.isEditing() ? t('events.sources.editEvents') : t('events.sources.addEvents')}
          </Modal.Header>
          <Modal.Content>
            <DefinitionSelection
              type="process"
              hideLatest
              definitionKey={processDefinitionKey}
              versions={versions}
              tenants={tenants}
              disableDefinition={this.isEditing()}
              expanded
              onChange={({key, name, versions, tenantIds}) => {
                this.setState(
                  {
                    processDefinitionName: name,
                    processDefinitionKey: key,
                    versions,
                    tenants: tenantIds,
                    traceVariable: null
                  },
                  this.loadVariables
                );
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
                  onChange={() => this.setState({tracedByBusinessKey: false})}
                  type="radio"
                  label={t('events.sources.byVariable')}
                />
                <Typeahead
                  loading={!variables}
                  value={variables && traceVariable}
                  noValuesMessage={getDisabledMessage(tracedByBusinessKey)}
                  disabled={tracedByBusinessKey}
                  onChange={traceVariable => this.setState({traceVariable})}
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
                  onChange={() => this.setState({tracedByBusinessKey: true})}
                  type="radio"
                  label={t('events.sources.byKey')}
                />
              </Form.Group>
              <Form.Group>
                <h4>{t('events.sources.display')}</h4>
                <LabeledInput
                  checked={eventScope === 'start_end'}
                  onChange={() => this.setState({eventScope: 'start_end'})}
                  label={t('events.sources.startAndEnd')}
                  type="radio"
                />
                <LabeledInput
                  checked={eventScope === 'process_instance'}
                  onChange={() => this.setState({eventScope: 'process_instance'})}
                  label={t('events.sources.flownodeEvents')}
                  type="radio"
                />
                <LabeledInput
                  label={t('events.sources.allEvents')}
                  checked={eventScope === 'all'}
                  onChange={() => this.setState({eventScope: 'all'})}
                  type="radio"
                />
              </Form.Group>
            </Form>
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

function getDisabledMessage(tracedByBusinessKey) {
  if (tracedByBusinessKey) {
    return t('common.none');
  }

  return t('events.sources.selectProcess');
}
