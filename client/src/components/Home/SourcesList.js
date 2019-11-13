/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {t} from 'translation';
import {Button, EntityList, Deleter} from 'components';
import {showError} from 'notifications';
import {withErrorHandling} from 'HOC';

import {getSources, addSources, editSource, removeSource} from './service';

import {ReactComponent as ProcessIcon} from './icons/process.svg';
import {ReactComponent as DecisionIcon} from './icons/decision.svg';
import AddSourceModal from './modals/AddSourceModal';
import EditSourceModal from './modals/EditSourceModal';

import './SourcesList.scss';

export default withErrorHandling(
  class SourcesList extends React.Component {
    state = {
      sources: null,
      deleting: null,
      editing: null,
      addingSource: false
    };

    componentDidMount() {
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
    editSource = tenants => {
      this.props.mightFail(
        editSource(this.props.collection, this.state.editing.id, tenants),
        this.getSources,
        showError
      );
      this.closeEditSourceModal();
    };
    closeEditSourceModal = () => this.setState({editing: null});

    render() {
      const {deleting, editing, addingSource, sources} = this.state;
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

                return {
                  className: definitionType,
                  icon: getSourceIcon(definitionType),
                  type: formatType(definitionType),
                  name: definitionName || definitionKey,
                  meta1: formatTenants(tenants),
                  actions: !readOnly && [
                    {
                      icon: 'edit',
                      text: t('common.edit'),
                      action: () => this.openEditSourceModal(source)
                    },
                    {
                      icon: 'delete',
                      text: t('common.delete'),
                      action: () => this.setState({deleting: source})
                    }
                  ]
                };
              })
            }
          />
          <Deleter
            entity={deleting}
            getName={() => deleting.definitionName || deleting.definitionKey}
            onDelete={this.getSources}
            onClose={() => this.setState({deleting: null})}
            deleteEntity={() => removeSource(collection, deleting.id)}
          />
          <AddSourceModal
            open={addingSource}
            onClose={this.closeAddSourceModal}
            onConfirm={this.addSource}
          />
          {editing && (
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
  return tenants.map(({name}) => name).join(', ');
}
