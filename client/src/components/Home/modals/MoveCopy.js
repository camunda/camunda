/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {showError} from 'notifications';

import {loadEntities} from '../service';
import {Form, Switch, Typeahead} from 'components';
import {withErrorHandling} from 'HOC';
import {t} from 'translation';

export default withErrorHandling(
  class MoveCopy extends React.Component {
    state = {
      availableCollections: [],
    };

    componentDidMount() {
      this.props.mightFail(
        loadEntities(),
        (entities) =>
          this.setState({
            availableCollections: [
              {id: null, entityType: 'collection', name: t('navigation.homepage')},
              ...entities,
            ].filter(
              ({entityType, id}) =>
                entityType === 'collection' && id !== this.props.parentCollection
            ),
          }),
        showError
      );
    }

    getMulticopyText = () => {
      const {entityType, data} = this.props.entity;
      const containedReports = data.subEntityCounts.report;

      if (containedReports) {
        const params = {
          entityType: entityType === 'dashboard' ? t('dashboard.label') : t('home.types.combined'),
          number: containedReports,
        };
        if (containedReports > 1) {
          return t('home.copy.subEntities', params);
        }
        return t('home.copy.subEntity', params);
      }
    };

    render() {
      const {moving, collection} = this.props;
      const {availableCollections} = this.state;
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
                  initialValue={collection ? collection.id : undefined}
                  noValuesMessage={t('home.copy.noCollections')}
                  placeholder={t('home.copy.pleaseSelect')}
                  onChange={(id) => {
                    const collection = availableCollections.find((col) => col.id === id);
                    this.props.setCollection(collection);
                  }}
                >
                  {availableCollections.map(({id, name}) => (
                    <Typeahead.Option key={id} value={id}>
                      {name}
                    </Typeahead.Option>
                  ))}
                </Typeahead>
              </Form.Group>
              {multicopyText && <Form.Group>{multicopyText}</Form.Group>}
            </>
          )}
        </>
      );
    }
  }
);
