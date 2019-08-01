/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Button, Icon, ConfirmationModal, Message, LoadingIndicator} from 'components';

import AlertModal from './subComponents/AlertModal';

import {withErrorHandling} from 'HOC';
import {
  formatters,
  isDurationReport,
  loadEntities,
  updateEntity,
  createEntity,
  deleteEntity
} from 'services';
import {addNotification} from 'notifications';

import './Alerts.scss';
import entityIcons from './entityIcons';

import LastModified from './subComponents/LastModified';
import NoEntities from './subComponents/NoEntities';
import {t} from 'translation';

const HeaderIcon = entityIcons.alert.header.Component;
const EntityIcon = entityIcons.alert.generic.Component;

const {duration, frequency} = formatters;

class Alerts extends React.Component {
  state = {
    reports: null,
    editEntity: null,
    loading: true,
    entities: [],
    deleting: false
  };

  loadAlerts = async () => {
    this.props.mightFail(loadEntities('alert', 'lastModified'), response => {
      this.setState({
        entities: response,
        loading: false
      });
    });
  };

  componentDidMount = async () => {
    this.loadAlerts();
    const reports = (await loadEntities('report', 'lastModified')).filter(
      ({combined, data: {visualization}}) => !combined && visualization === 'number'
    );
    this.setState({
      reports
    });
  };

  showDeleteModalFor = alert => () => this.setState({deleting: alert});
  hideDeleteModal = () => this.setState({deleting: false});

  deleteAlert = () => {
    deleteEntity('alert', this.state.deleting.id);
    this.setState(({entities, deleting}) => {
      return {
        entities: entities.filter(entity => entity !== deleting),
        deleting: false
      };
    });
  };

  renderMetadata = ({reportId, email, thresholdOperator, threshold}) => {
    if (!this.state.reports) {
      return;
    }
    const report = this.state.reports.find(({id}) => reportId === id);
    const aboveOrBelow = thresholdOperator === '<' ? t('alert.below') : t('alert.above');
    const thresholdValue = isDurationReport(report) ? duration(threshold) : frequency(threshold);
    return (
      <span
        className="metadata"
        dangerouslySetInnerHTML={{
          __html: t('alert.description', {email, name: report.name, aboveOrBelow, thresholdValue})
        }}
      />
    );
  };

  showEditModalFor = alert => () => {
    this.setState({editEntity: alert});
  };
  showCreateModal = this.showEditModalFor({});
  closeEditModal = () => this.setState({editEntity: null});

  updateOrCreateAlert = async entity => {
    const editEntity = this.state.editEntity;
    let updatePromise;
    if (editEntity.id) {
      updatePromise = updateEntity('alert', editEntity.id, entity);
    } else {
      updatePromise = createEntity('alert', null, entity);
    }

    await this.props.mightFail(
      updatePromise,
      () => {
        this.closeEditModal();
        this.loadAlerts();
      },
      () => {
        addNotification({text: t('alert.cannotSave', {name: entity.name}), type: 'error'});
      }
    );
  };

  render() {
    const {reports} = this.state;

    const EditModal = this.state.editEntity && AlertModal(reports);

    const error = this.props.error && (
      <Message type="error">{this.props.error.errorMessage || this.props.error.statusText}</Message>
    );

    const loading = this.state.loading && <LoadingIndicator />;

    const empty = !loading && this.state.entities.length === 0 && (
      <NoEntities label="Alert" createFunction={this.showCreateModal} />
    );

    return (
      <div className="Alerts">
        <h1>
          <HeaderIcon /> {t('alert.label-plural')}
        </h1>
        <Button
          color="blue"
          variant="primary"
          className="createButton"
          onClick={this.showCreateModal}
        >
          {t('alert.createNew')}
        </Button>
        {error}
        {loading}
        <ul className="entityList">
          {empty}
          {this.state.entities.map((itemData, idx) => (
            <li key={idx}>
              <span className="info" onClick={this.showEditModalFor(itemData)}>
                <span className="icon">
                  <EntityIcon />
                </span>
                <div className="textInfo">
                  <div className="data dataTitle">
                    <h3>{itemData.name}</h3>
                  </div>
                  <div className="extraInfo">
                    <span className="data custom">{this.renderMetadata(itemData)}</span>
                    <LastModified
                      label={t('common.entity.changed')}
                      date={itemData.lastModified}
                      author={itemData.lastModifier}
                    />
                  </div>
                </div>
              </span>
              <div className="operations">
                <Button title={t('alert.edit')} onClick={this.showEditModalFor(itemData)}>
                  <Icon type="edit" className="editLink" />
                </Button>
                <Button title={t('alert.delete')} onClick={this.showDeleteModalFor(itemData)}>
                  <Icon type="delete" className="deleteIcon" />
                </Button>
              </div>
            </li>
          ))}
        </ul>
        {this.state.deleting && (
          <ConfirmationModal
            onClose={this.hideDeleteModal}
            onConfirm={this.deleteAlert}
            entityName={this.state.deleting && this.state.deleting.name}
          />
        )}
        {EditModal && (
          <EditModal
            onConfirm={this.updateOrCreateAlert}
            onClose={this.closeEditModal}
            entity={this.state.editEntity}
          />
        )}
      </div>
    );
  }
}

export default withErrorHandling(Alerts);
