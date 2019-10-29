/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';

import {Button, LabeledInput, Modal, Form, Switch, Typeahead, InfoMessage} from 'components';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import {loadEntities} from './service';

import './CopyModal.scss';

export default withErrorHandling(
  class CopyModal extends Component {
    constructor(props) {
      super(props);

      this.state = {
        name: props.entity.name + ` (${t('common.copyLabel')})`,
        moving: false,
        availableCollections: [],
        collection: null,
        gotoNew: true
      };
    }

    componentDidMount() {
      if (this.props.entity.entityType !== 'collection') {
        this.props.mightFail(
          loadEntities(),
          entities =>
            this.setState({
              availableCollections: [
                {id: null, entityType: 'collection', name: t('navigation.homepage')},
                ...entities
              ].filter(
                ({entityType, id}) => entityType === 'collection' && id !== this.props.collection
              )
            }),
          showError
        );
      }
    }

    onConfirm = () => {
      const {name, moving, collection, gotoNew} = this.state;
      if (name && (!moving || collection)) {
        if (this.isCollection() && this.props.jumpToEntity) {
          this.props.onConfirm(name, gotoNew);
        } else {
          this.props.onConfirm(name, moving && gotoNew, moving && collection.id);
        }
      }
    };

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

    renderMoveOption = () => {
      const {moving, availableCollections, collection} = this.state;
      const multicopyText = this.getMulticopyText();

      return (
        <>
          <Form.Group className="moveSection">
            <Switch
              label={t('home.copy.moveLabel')}
              checked={moving}
              onChange={({target: {checked}}) => this.setState({moving: checked})}
            />
          </Form.Group>
          {moving && (
            <>
              <Form.Group noSpacing>
                <Typeahead
                  initialValue={collection}
                  noValuesMessage={t('home.copy.noCollections')}
                  placeholder={t('home.copy.pleaseSelect')}
                  values={availableCollections}
                  formatter={({name}) => name}
                  onSelect={collection => this.setState({collection})}
                />
              </Form.Group>
              {multicopyText && <Form.Group>{multicopyText}</Form.Group>}
            </>
          )}
        </>
      );
    };

    isCollection = () => this.props.entity.entityType === 'collection';

    render() {
      const {onClose, entity, jumpToEntity} = this.props;
      const {name, moving, collection, gotoNew} = this.state;

      return (
        <Modal className="CopyModal" open onClose={onClose} onConfirm={this.onConfirm}>
          <Modal.Header>{t('common.copyName', {name: entity.name})}</Modal.Header>
          <Modal.Content>
            <Form>
              <Form.Group>
                <LabeledInput
                  type="text"
                  label={t('home.copy.inputLabel')}
                  value={name}
                  autoComplete="off"
                  onChange={({target: {value}}) => this.setState({name: value})}
                />
              </Form.Group>
              {this.isCollection() && (
                <InfoMessage>{t('home.copy.copyCollectionInfo')}</InfoMessage>
              )}
              {!this.isCollection() && this.renderMoveOption()}
              {jumpToEntity && (this.isCollection() || moving) && (
                <Form.Group>
                  <LabeledInput
                    label={t('home.copy.gotoNew')}
                    type="checkbox"
                    checked={gotoNew}
                    onChange={({target: {checked}}) => this.setState({gotoNew: checked})}
                  />
                </Form.Group>
              )}
            </Form>
          </Modal.Content>
          <Modal.Actions>
            <Button className="cancel" onClick={onClose}>
              {t('common.cancel')}
            </Button>
            <Button
              disabled={!name || (moving && !collection)}
              variant="primary"
              color="blue"
              className="confirm"
              onClick={this.onConfirm}
            >
              {t('common.copy')}
            </Button>
          </Modal.Actions>
        </Modal>
      );
    }
  }
);
