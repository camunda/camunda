/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import equal from 'deep-equal';

import {LoadingIndicator, Form, Labeled, Checklist} from 'components';
import {t} from 'translation';
import {formatDefinitions} from './service';

export default class MultiDefinitionSource extends React.Component {
  state = {
    selectedDefinitions: [],
  };

  componentDidUpdate(_, prevState) {
    const {selectedDefinitions} = this.state;
    if (!equal(prevState.selectedDefinitions, selectedDefinitions)) {
      if (selectedDefinitions.length) {
        const sources = selectedDefinitions.map(({type, key}) => ({
          definitionType: type,
          definitionKey: key,
          tenants: [null],
        }));
        this.props.onChange(sources);
      } else {
        this.props.setInvalid();
      }
    }
  }

  render() {
    const {selectedDefinitions} = this.state;
    const {definitions} = this.props;

    if (!definitions) {
      return <LoadingIndicator />;
    }

    return (
      <Form.Group>
        <Labeled label={t('home.sources.definition.label-plural')}>
          <Checklist
            selectedItems={selectedDefinitions}
            allItems={definitions}
            onChange={(selectedDefinitions) => this.setState({selectedDefinitions})}
            formatter={formatDefinitions}
          />
        </Labeled>
      </Form.Group>
    );
  }
}
