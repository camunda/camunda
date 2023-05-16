/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Button} from '@carbon/react';

import {t} from 'translation';
import {EntityList, Deleter, BulkDeleter, Modal, Button as LegacyButton} from 'components';
import {showError} from 'notifications';
import {withErrorHandling} from 'HOC';
import {formatters} from 'services';
import {areTenantsAvailable} from 'config';

import {
  getSources,
  addSources,
  editSource,
  removeSource,
  checkDeleteSourceConflicts,
  checkSourcesConflicts,
  removeSources,
} from './service';
import SourcesModal from './modals/SourcesModal';
import EditSourceModal from './modals/EditSourceModal';

import './SourcesList.scss';

const {formatTenantName} = formatters;

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
      tenants: [],
    };

    async componentDidMount() {
      this.setState({tenantsAvailable: await areTenantsAvailable()});
      this.getSources();
    }

    getSources = () => {
      this.props.mightFail(
        getSources(this.props.collection),
        (sources) => this.setState({sources}),
        showError
      );
    };

    openAddSourceModal = () => this.setState({addingSource: true});
    addSource = (sources) => {
      this.closeAddSourceModal();
      this.props.mightFail(addSources(this.props.collection, sources), this.getSources, showError);
    };
    closeAddSourceModal = () => this.setState({addingSource: false});

    openEditSourceModal = (editing) => this.setState({editing});
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
        (error) => {
          if (error.status === 409 && error.conflictedItems) {
            this.setState({
              editLoading: false,
              conflict: error.conflictedItems,
              tenants,
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
        tenants,
      } = this.state;

      const {readOnly, collection} = this.props;

      return (
        <div className="SourcesList">
          <EntityList
            name={t('home.sources.title')}
            action={(bulkActive) =>
              !readOnly && (
                <LegacyButton
                  main
                  primary={!bulkActive}
                  onClick={() => this.setState({addingSource: true})}
                >
                  {t('common.add')}
                </LegacyButton>
              )
            }
            bulkActions={
              !readOnly && [
                <BulkDeleter
                  type="remove"
                  deleteEntities={async (selectedSources) =>
                    await removeSources(collection, selectedSources)
                  }
                  checkConflicts={async (selectedSources) =>
                    await checkSourcesConflicts(collection, selectedSources)
                  }
                  conflictMessage={t('common.deleter.affectedMessage.bulk.process')}
                />,
              ]
            }
            onChange={() => {
              this.getSources();
              this.props.onChange();
            }}
            empty={
              <>
                {t('home.sources.notCreated')}
                {readOnly && (
                  <>
                    <br />
                    {t('home.sources.contactManager')}
                  </>
                )}
              </>
            }
            isLoading={!sources}
            columns={[t('home.sources.definitionName'), t('common.tenant.label-plural')]}
            data={
              sources &&
              sources.map((source) => {
                const {id, definitionKey, definitionName, definitionType, tenants} = source;
                const actions = [];
                if (!readOnly) {
                  if (!hasUnauthorized(tenants)) {
                    actions.push({
                      icon: 'delete',
                      text: t('common.remove'),
                      action: () => this.setState({deleting: source}),
                    });
                  }

                  if (tenantsAvailable) {
                    actions.unshift({
                      icon: 'edit',
                      text: t('common.edit'),
                      action: () => this.openEditSourceModal(source),
                    });
                  }
                }

                return {
                  id,
                  entityType: 'process',
                  className: definitionType,
                  icon: 'data-source',
                  type: formatType(definitionType),
                  name: definitionName || definitionKey,
                  meta: [tenantsAvailable && formatTenants(tenants)],
                  actions,
                };
              })
            }
          />
          <Deleter
            entity={deleting}
            getName={() => deleting.definitionName || deleting.definitionKey}
            deleteText={t('common.removeEntity', {entity: t('common.deleter.types.source')})}
            descriptionText={t('home.sources.deleteWarning', {
              name: (deleting && (deleting.definitionName || deleting.definitionKey)) || '',
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
          <Modal open={conflict} onClose={this.closeEditSourceModal} className="saveModal">
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
            <Modal.Footer>
              <Button
                kind="secondary"
                disabled={editLoading}
                className="close"
                onClick={this.closeEditSourceModal}
              >
                {t('saveGuard.no')}
              </Button>
              <Button
                disabled={editLoading}
                className="confirm"
                onClick={() => this.editSource(tenants, true)}
              >
                {t('saveGuard.yes')}
              </Button>
            </Modal.Footer>
          </Modal>
          {addingSource && (
            <SourcesModal
              onClose={this.closeAddSourceModal}
              onConfirm={this.addSource}
              confirmText={t('common.add')}
            />
          )}
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

  return tenants.map(formatTenantName).join(', ');
}
