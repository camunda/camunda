/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {t} from 'translation';
import {Button, EntityList, Deleter, Modal} from 'components';
import {showError} from 'notifications';
import {withErrorHandling} from 'HOC';

import {
  getSources,
  addSources,
  editSource,
  removeSource,
  checkDeleteSourceConflicts
} from './service';

import {ReactComponent as ProcessIcon} from './icons/process.svg';
import {ReactComponent as DecisionIcon} from './icons/decision.svg';
import AddSourceModal from './modals/AddSourceModal';
import EditSourceModal from './modals/EditSourceModal';
import {areTenantsAvailable} from 'config';

import './SourcesList.scss';

export default withErrorHandling(
  class SourcesList extends React.Component {
    state = {
      sources: null,
      deleting: null,
      editing: null,
      addingSource: false,
      tenantsAvailable: false,
      conflict: null,
      editLoading: false,
      tenants: []
    };

    async componentDidMount() {
      this.setState({tenantsAvailable: await areTenantsAvailable()});
      this.getSources();
    }

    getSources = () => {
      this.props.mightFail(
        getSources(this.props.collection),
        sources => this.setState({sources}),
        showError
      );
    };

    openAddSourceModal = () => this.setState({addingSource: true});
    addSource = sources => {
      this.closeAddSourceModal();
      this.props.mightFail(addSources(this.props.collection, sources), this.getSources, showError);
    };
    closeAddSourceModal = () => this.setState({addingSource: false});

    openEditSourceModal = editing => this.setState({editing});
    editSource = (tenants, force) => {
      const {id} = this.state.editing;
      this.setState({editLoading: true});
      this.props.mightFail(
        editSource(this.props.collection, id, tenants, force),
        () => {
          this.closeEditSourceModal();
          this.getSources();
          if (force) {
            this.props.onChange();
          }
        },
        async error => {
          if (error.statusText === 'Conflict') {
            const {conflictedItems} = await error.json();
            this.setState({
              editLoading: false,
              conflict: conflictedItems,
              tenants
            });
          } else {
            showError(error);
          }
        }
      );
    };
    closeEditSourceModal = () =>
      this.setState({conflict: null, editing: null, editLoading: false, tenants: []});

    render() {
      const {
        deleting,
        editing,
        editLoading,
        addingSource,
        sources,
        tenantsAvailable,
        conflict,
        tenants
      } = this.state;

      const {readOnly, collection} = this.props;

      return (
        <div className="SourcesList">
          <EntityList
            name={t('home.sources.title')}
            action={
              !readOnly && (
                <Button onClick={() => this.setState({addingSource: true})}>
                  {t('common.add')}
                </Button>
              )
            }
            empty={t('home.sources.notCreated')}
            isLoading={!sources}
            data={
              sources &&
              sources.map(source => {
                const {definitionKey, definitionName, definitionType, tenants} = source;
                const actions = [];
                if (!readOnly) {
                  if (!hasUnauthorized(tenants)) {
                    actions.push({
                      icon: 'delete',
                      text: t('common.delete'),
                      action: () => this.setState({deleting: source})
                    });
                  }

                  if (tenantsAvailable) {
                    actions.unshift({
                      icon: 'edit',
                      text: t('common.edit'),
                      action: () => this.openEditSourceModal(source)
                    });
                  }
                }

                return {
                  className: definitionType,
                  icon: getSourceIcon(definitionType),
                  type: formatType(definitionType),
                  name: definitionName || definitionKey,
                  meta1: tenantsAvailable && formatTenants(tenants),
                  actions
                };
              })
            }
          />
          <Deleter
            entity={deleting}
            getName={() => deleting.definitionName || deleting.definitionKey}
            deleteText={t('common.remove')}
            descriptionText={t('home.sources.deleteWarning', {
              name: (deleting && (deleting.definitionName || deleting.definitionKey)) || ''
            })}
            onDelete={() => {
              this.getSources();
              this.props.onChange();
            }}
            checkConflicts={async () => await checkDeleteSourceConflicts(collection, deleting.id)}
            onClose={() => this.setState({deleting: null})}
            type="source"
            deleteEntity={() => removeSource(collection, deleting.id)}
          />
          <Modal
            open={conflict}
            onClose={this.closeEditSourceModal}
            onConfirm={() => this.editSource(tenants, true)}
            className="saveModal"
          >
            <Modal.Header>{t('report.saveConflict.header')}</Modal.Header>
            <Modal.Content>
              {conflict && conflict.length > 0 && (
                <>
                  <p>{t('home.sources.saveConflict.header')}</p>
                  <ul>
                    {conflict.map(({id, name}) => (
                      <li key={id}>'{name || id}'</li>
                    ))}
                  </ul>
                  <p>
                    <b>{t('home.sources.saveConflict.message')}</b>
                  </p>
                </>
              )}
            </Modal.Content>
            <Modal.Actions>
              <Button disabled={editLoading} className="close" onClick={this.closeEditSourceModal}>
                {t('saveGuard.no')}
              </Button>
              <Button
                disabled={editLoading}
                variant="primary"
                color="blue"
                className="confirm"
                onClick={() => this.editSource(tenants, true)}
              >
                {t('saveGuard.yes')}
              </Button>
            </Modal.Actions>
          </Modal>
          <AddSourceModal
            open={addingSource}
            onClose={this.closeAddSourceModal}
            onConfirm={this.addSource}
            tenantsAvailable={tenantsAvailable}
          />
          {editing && !conflict && (
            <EditSourceModal
              source={editing}
              onClose={this.closeEditSourceModal}
              onConfirm={this.editSource}
            />
          )}
        </div>
      );
    }
  }
);

function hasUnauthorized(tenants) {
  return tenants.some(({id}) => id === '__unauthorizedTenantId__');
}

function getSourceIcon(type) {
  switch (type) {
    case 'process':
      return <ProcessIcon />;
    case 'decision':
      return <DecisionIcon />;
    default:
      return <ProcessIcon />;
  }
}

function formatType(type) {
  switch (type) {
    case 'process':
      return t('home.sources.process');
    case 'decision':
      return t('home.sources.decision');
    default:
      return t('home.types.unknown');
  }
}

function formatTenants(tenants) {
  if (tenants.length === 1 && tenants[0].id === null) {
    return '';
  }

  return tenants
    .map(({id, name}) => {
      if (id === '__unauthorizedTenantId__') {
        return t('home.sources.unauthorizedTenant');
      }
      return name;
    })
    .join(', ');
}
