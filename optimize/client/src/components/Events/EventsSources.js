/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState} from 'react';
import update from 'immutability-helper';
import {Button, MenuButton, TableToolbarSearch, MenuItem} from '@carbon/react';
import {Add} from '@carbon/icons-react';
import classnames from 'classnames';

import {Deleter} from 'components';
import EventsSourceModal from './EventsSourceModal';
import VisibleEventsModal from './VisibleEventsModal';
import {t} from 'translation';

import './EventsSources.scss';

export default function EventsSources({sources, onChange, searchFor, searchQuery}) {
  const [editing, setEditing] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const [editingScope, setEditingScope] = useState(null);

  const openAddSourceModal = () => setEditing({});
  const openEditSourceModal = (editing) => setEditing(editing);
  const closeSourceModal = () => setEditing(null);
  const openEditScopeModal = (editingScope) => setEditingScope(editingScope);
  const closeEditScopeModal = () => setEditingScope(null);

  const removeSource = (target) => {
    onChange(
      sources.filter((src) => src !== target),
      true
    );
  };

  const toggleSource = (targetSource) => {
    const sourceIndex = sources.indexOf(targetSource);
    onChange(update(sources, {[sourceIndex]: {$toggle: ['hidden']}}), false);
  };

  const updateSourceScope = (newScope) => {
    const sourceIndex = sources.findIndex(
      ({configuration: {processDefinitionKey}}) =>
        processDefinitionKey === editingScope.configuration.processDefinitionKey
    );
    const updateSources = update(sources, {
      [sourceIndex]: {configuration: {eventScope: {$set: newScope}}},
    });
    onChange(updateSources, true);
    closeEditScopeModal();
  };

  return (
    <div className="EventsSources">
      <div className="sourcesList">
        {sources.map((source) => {
          return (
            <MenuButton
              {...getDropdownProps(source)}
              kind="tertiary"
              size="sm"
              className={classnames('SourceMenuButton', {hidden: source.hidden})}
            >
              <MenuItem
                onClick={() => toggleSource(source)}
                label={source.hidden ? t('events.sources.show') : t('events.sources.hide')}
              />
              {source.type !== 'external' && (
                <>
                  <MenuItem
                    onClick={() => openEditSourceModal(source)}
                    label={t('events.sources.editSource')}
                  />
                  <MenuItem
                    onClick={() => openEditScopeModal(source)}
                    label={t('events.sources.editScope')}
                  />
                </>
              )}
              <MenuItem onClick={() => setDeleting(source)} label={t('events.sources.remove')} />
            </MenuButton>
          );
        })}
      </div>
      <TableToolbarSearch
        expanded
        value={searchQuery}
        placeholder={t('home.search.name')}
        onChange={({target: {value}}) => searchFor(value)}
        onClear={() => searchFor('')}
      />
      <Button renderIcon={Add} className="addProcess" onClick={openAddSourceModal}>
        {t('events.sources.add')}
      </Button>
      <Deleter
        type="processEvents"
        deleteText={t('common.removeEntity', {entity: t('common.deleter.types.processEvents')})}
        entity={deleting}
        deleteEntity={removeSource}
        onClose={() => setDeleting(null)}
        descriptionText={t('events.sources.deleteWarning')}
      />
      {editing && (
        <EventsSourceModal
          initialSource={editing}
          existingSources={sources}
          onConfirm={(newSources, isEditing) => {
            onChange(newSources, isEditing);
            closeSourceModal();
          }}
          onClose={closeSourceModal}
        />
      )}
      {editingScope && (
        <VisibleEventsModal
          initialScope={editingScope.configuration.eventScope}
          onConfirm={updateSourceScope}
          onClose={closeEditScopeModal}
        />
      )}
    </div>
  );
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
