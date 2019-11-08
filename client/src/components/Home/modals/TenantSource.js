/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';
import Checklist from './Checklist';
import {Typeahead, LoadingIndicator, Labeled, Form} from 'components';
import {t} from 'translation';
import equal from 'deep-equal';
import {formatDefintionName} from './service';

export default class TenantSource extends Component {
  state = {
    selectedTenant: null,
    selectedDefinitions: []
  };

  componentDidUpdate(_, prevState) {
    const {selectedTenant, selectedDefinitions} = this.state;
    if (
      !equal(prevState.selectedTenant, selectedTenant) ||
      !equal(prevState.selectedDefinitions, selectedDefinitions)
    ) {
      this.onChange();
    }
  }

  onChange = () => {
    const {selectedTenant, selectedDefinitions} = this.state;
    if (selectedTenant && selectedDefinitions.length) {
      const sources = selectedDefinitions.map(({type, key}) => ({
        definitionType: type,
        definitionKey: key,
        tenants: [selectedTenant.id]
      }));
      this.props.onChange(sources);
    } else {
      this.props.setInvalid();
    }
  };

  updateSelectedDefinitions = (key, checked) => {
    let newDefinitions;
    if (checked) {
      const definitionsToSelect = this.state.selectedTenant.definitions.find(
        def => def.key === key
      );
      newDefinitions = [...this.state.selectedDefinitions, definitionsToSelect];
    } else {
      newDefinitions = this.state.selectedDefinitions.filter(def => def.key !== key);
    }
    this.setState({selectedDefinitions: newDefinitions});
  };

  selectTenant = selectedTenant => {
    let tenantToSelect;
    if (!selectedTenant.id) {
      tenantToSelect = selectedTenant;
    } else {
      tenantToSelect = {
        ...selectedTenant,
        definitions: [
          ...selectedTenant.definitions,
          ...this.props.tenantsWithDefinitions[0].definitions
        ]
      };
    }

    this.setState({selectedTenant: tenantToSelect, selectedDefinitions: []});
  };

  render() {
    const {selectedTenant, selectedDefinitions} = this.state;
    const {tenantsWithDefinitions} = this.props;

    if (!tenantsWithDefinitions) {
      return <LoadingIndicator />;
    }

    return (
      <>
        <Form.Group>
          <Labeled label={t('common.tenant.label-plural')}>
            <Typeahead
              disabled={tenantsWithDefinitions.length === 0}
              placeholder={t('common.select')}
              values={tenantsWithDefinitions}
              onSelect={this.selectTenant}
              formatter={({name}) => name}
              noValuesMessage={t('common.notFound')}
            />
          </Labeled>
        </Form.Group>
        {selectedTenant && (
          <Form.Group>
            <Labeled label={t('home.sources.definition.label-plural')}>
              <Checklist
                data={selectedTenant.definitions}
                onChange={this.updateSelectedDefinitions}
                selectAll={() => this.setState({selectedDefinitions: selectedTenant.definitions})}
                deselectAll={() => this.setState({selectedDefinitions: []})}
                formatter={({key, name, type}) => ({
                  id: key,
                  label: formatDefintionName({name, type}),
                  checked: selectedDefinitions.some(def => def.key === key)
                })}
              />
            </Labeled>
          </Form.Group>
        )}
      </>
    );
  }
}
