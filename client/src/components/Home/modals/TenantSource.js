/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import ItemsList from './ItemsList';
import {Typeahead, LoadingIndicator, Labeled, Form} from 'components';
import {t} from 'translation';
import equal from 'deep-equal';
import {formatDefinitions} from './service';
import {formatters} from 'services';

const {formatTenantName} = formatters;

export default class TenantSource extends React.Component {
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
    }
  }

  selectTenant = selectedTenant => {
    this.setState({selectedTenant, selectedDefinitions: []});
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
              formatter={formatTenantName}
              noValuesMessage={t('common.notFound')}
            />
          </Labeled>
        </Form.Group>
        {selectedTenant && (
          <Form.Group>
            <Labeled label={t('home.sources.definition.label-plural')}>
              <ItemsList
                selectedItems={selectedDefinitions}
                allItems={selectedTenant.definitions}
                onChange={selectedDefinitions => this.setState({selectedDefinitions})}
                formatter={formatDefinitions}
              />
            </Labeled>
          </Form.Group>
        )}
      </>
    );
  }
}
