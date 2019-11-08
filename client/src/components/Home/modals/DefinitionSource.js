/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';
import Checklist from './Checklist';
import {Typeahead, LoadingIndicator, Form, Labeled} from 'components';
import {t} from 'translation';
import equal from 'deep-equal';
import {formatDefintionName} from './service';

export default class DefinitionSource extends Component {
  state = {
    selectedDefintion: null,
    selectedTenants: []
  };

  componentDidUpdate(_, prevState) {
    const {selectedDefintion, selectedTenants} = this.state;
    if (
      !equal(prevState.selectedDefintion, selectedDefintion) ||
      !equal(prevState.selectedTenants, selectedTenants)
    ) {
      this.onChange();
    }
  }

  onChange = () => {
    const {selectedDefintion, selectedTenants} = this.state;
    if (selectedDefintion && selectedTenants.length) {
      this.props.onChange({
        definitionType: selectedDefintion.type,
        definitionKey: selectedDefintion.key,
        tenants: selectedTenants.map(({id}) => id)
      });
    } else {
      this.props.setInvalid();
    }
  };

  updateSelectedTenants = (id, checked) => {
    let newTenants;
    if (checked) {
      const tenantsToSelect = this.state.selectedDefintion.tenants.find(tenant => tenant.id === id);
      newTenants = [...this.state.selectedTenants, tenantsToSelect];
    } else {
      newTenants = this.state.selectedTenants.filter(tenant => tenant.id !== id);
    }
    this.setState({selectedTenants: newTenants});
  };

  updateSelectedDefinition = selectedDefintion => {
    const newSelectionState = {selectedDefintion, selectedTenants: []};
    if (selectedDefintion.tenants.length === 1) {
      // preselect if there is only one tenant
      newSelectionState.selectedTenants = [selectedDefintion.tenants[0]];
    } else {
      newSelectionState.selectedTenants = [];
    }

    this.setState(newSelectionState);
  };

  render() {
    const {selectedDefintion, selectedTenants} = this.state;
    const {definitionsWithTenants} = this.props;

    if (!definitionsWithTenants) {
      return <LoadingIndicator />;
    }

    return (
      <>
        <Form.Group>
          <Labeled label={t('home.sources.definition.label-plural')}>
            <Typeahead
              disabled={definitionsWithTenants.length === 0}
              placeholder={t('common.select')}
              values={definitionsWithTenants}
              onSelect={this.updateSelectedDefinition}
              formatter={formatDefintionName}
              noValuesMessage={t('common.definitionSelection.noDefinition')}
            />
          </Labeled>
        </Form.Group>
        {selectedDefintion && selectedDefintion.tenants.length !== 1 && (
          <Form.Group>
            <Labeled label={t('common.tenant.label-plural')}>
              <Checklist
                data={selectedDefintion.tenants}
                onChange={this.updateSelectedTenants}
                selectAll={() => this.setState({selectedTenants: selectedDefintion.tenants})}
                deselectAll={() => this.setState({selectedTenants: []})}
                formatter={({id, name}) => ({
                  id,
                  label: name,
                  checked: selectedTenants.some(tenant => tenant.id === id)
                })}
              />
            </Labeled>
          </Form.Group>
        )}
      </>
    );
  }
}
