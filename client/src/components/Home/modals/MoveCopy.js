/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';
import {showError} from 'notifications';

import {loadEntities} from '../service';
import {Form, Switch, Typeahead} from 'components';
import {withErrorHandling} from 'HOC';
import {t} from 'translation';

export default withErrorHandling(
  class MoveCopy extends Component {
    state = {
      availableCollections: []
    };

    componentDidMount() {
      this.props.mightFail(
        loadEntities(),
        entities =>
          this.setState({
            availableCollections: [
              {id: null, entityType: 'collection', name: t('navigation.homepage')},
              ...entities
            ].filter(
              ({entityType, id}) =>
                entityType === 'collection' && id !== this.props.parentCollection
            )
          }),
        showError
      );
    }

    getMulticopyText = () => {
      const {entityType, data} = this.props.entity;
      const containedReports = data.subEntityCounts.report;

      if (containedReports) {
        return t(containedReports > 1 ? 'home.copy.subEntities' : 'home.copy.subEntity', {
          entityType: entityType === 'dashboard' ? t('dashboard.label') : t('home.types.combined'),
          number: containedReports
        });
      }
    };

    render() {
      const {moving, collection} = this.props;
      const multicopyText = this.getMulticopyText();

      return (
        <>
          <Form.Group className="moveSection">
            <Switch
              label={t('home.copy.moveLabel')}
              checked={moving}
              onChange={({target: {checked}}) => this.props.setMoving(checked)}
            />
          </Form.Group>
          {moving && (
            <>
              <Form.Group noSpacing>
                <Typeahead
                  initialValue={collection}
                  noValuesMessage={t('home.copy.noCollections')}
                  placeholder={t('home.copy.pleaseSelect')}
                  values={this.state.availableCollections}
                  formatter={({name}) => name}
                  onSelect={this.props.setCollection}
                />
              </Form.Group>
              {multicopyText && <Form.Group>{multicopyText}</Form.Group>}
            </>
          )}
        </>
      );
    }
  }
);
