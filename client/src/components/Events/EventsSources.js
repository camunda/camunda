/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import update from 'immutability-helper';
import classnames from 'classnames';

import {Dropdown, Button, Icon, Deleter} from 'components';
import EventsSourceModal from './EventsSourceModal';
import VisibleEventsModal from './VisibleEventsModal';
import {t} from 'translation';

import './EventsSources.scss';

export default class EventSources extends React.Component {
  state = {
    editing: null,
    deleting: null,
    editingScope: null,
  };

  openAddSourceModal = () => this.setState({editing: {}});
  openEditSourceModal = (editing) => this.setState({editing});
  closeSourceModal = () => this.setState({editing: null});
  openEditScopeModal = (editingScope) => this.setState({editingScope});
  closeEditScopeModal = () => this.setState({editingScope: null});

  removeSource = (target) => {
    this.props.onChange(
      this.props.sources.filter((src) => src !== target),
      true
    );
  };

  toggleSource = (targetSource) => {
    const sourceIndex = this.props.sources.indexOf(targetSource);
    this.props.onChange(update(this.props.sources, {[sourceIndex]: {$toggle: ['hidden']}}), false);
  };

  updateSourceScope = (newScope) => {
    const sourceIndex = this.props.sources.findIndex(
      ({configuration: {processDefinitionKey}}) =>
        processDefinitionKey === this.state.editingScope.configuration.processDefinitionKey
    );
    const updateSources = update(this.props.sources, {
      [sourceIndex]: {configuration: {eventScope: {$set: newScope}}},
    });
    this.props.onChange(updateSources, true);
    this.closeEditScopeModal();
  };

  render() {
    const {editing, deleting, editingScope} = this.state;
    const {sources} = this.props;

    return (
      <div className="EventsSources">
        <div className="sourcesList">
          {sources.map((source) => {
            return (
              <Dropdown
                className={classnames({isActive: !source.hidden})}
                {...getDropdownProps(source)}
              >
                <Dropdown.Option onClick={() => this.toggleSource(source)}>
                  {source.hidden ? t('events.sources.show') : t('events.sources.hide')}
                </Dropdown.Option>
                {source.type !== 'external' && (
                  <>
                    <Dropdown.Option onClick={() => this.openEditSourceModal(source)}>
                      {t('events.sources.editSource')}
                    </Dropdown.Option>
                    <Dropdown.Option onClick={() => this.openEditScopeModal(source)}>
                      {t('events.sources.editScope')}
                    </Dropdown.Option>
                  </>
                )}
                <Dropdown.Option onClick={() => this.setState({deleting: source})}>
                  {t('events.sources.remove')}
                </Dropdown.Option>
              </Dropdown>
            );
          })}
        </div>
        <Button className="addProcess" onClick={this.openAddSourceModal}>
          <Icon type="plus" size="14" />
          {t('events.sources.add')}
        </Button>
        <Deleter
          type="processEvents"
          deleteText={t('common.removeEntity', {entity: t('common.deleter.types.processEvents')})}
          entity={deleting}
          deleteEntity={this.removeSource}
          onClose={() => this.setState({deleting: null})}
          descriptionText={t('events.sources.deleteWarning')}
        />
        {editing && (
          <EventsSourceModal
            initialSource={editing}
            existingSources={sources}
            onConfirm={(newSources, isEditing) => {
              this.props.onChange(newSources, isEditing);
              this.closeSourceModal();
            }}
            onClose={this.closeSourceModal}
          />
        )}
        {editingScope && (
          <VisibleEventsModal
            initialScope={editingScope.configuration.eventScope}
            onConfirm={this.updateSourceScope}
            onClose={this.closeEditScopeModal}
          />
        )}
      </div>
    );
  }
}

function getDropdownProps({configuration, type}) {
  if (type === 'external') {
    const {includeAllGroups, group} = configuration;
    const groupName = group === null ? t('events.sources.ungrouped') : group;
    const label = includeAllGroups ? t('events.sources.externalEvents') : groupName;
    return {
      label,
      key: label,
    };
  } else {
    const {processDefinitionKey, processDefinitionName} = configuration;
    return {
      label: processDefinitionName || processDefinitionKey,
      key: processDefinitionKey,
    };
  }
}
