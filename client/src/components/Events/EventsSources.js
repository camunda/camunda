/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Dropdown, Button, Icon, Deleter} from 'components';
import './EventsSources.scss';
import classnames from 'classnames';
import EventsSourceModal from './EventsSourceModal';
import {t} from 'translation';

export default class EventSources extends React.Component {
  state = {
    editing: null,
    deleting: null
  };

  openAddSourceModal = () => this.setState({editing: {}});
  openEditSourceModal = editing => this.setState({editing});
  closeSourceModal = () => this.setState({editing: null});
  removeSource = ({processDefinitionKey}) => {
    const {sources} = this.props;
    const filteredSources = sources.filter(
      source => source.processDefinitionKey !== processDefinitionKey
    );
    this.props.onChange(filteredSources);
  };

  render() {
    const {editing, deleting} = this.state;
    const {sources} = this.props;

    return (
      <div className="EventsSources">
        <div className="sourcesList">
          <div className="external">{t('events.sources.externalEvents')}</div>
          {sources.map(source => {
            const {processDefinitionKey, processDefinitionName} = source;
            return (
              <Dropdown
                className={classnames({isActive: true})}
                key={processDefinitionKey}
                label={processDefinitionName || processDefinitionKey}
              >
                <Dropdown.Option onClick={() => this.openEditSourceModal(source)}>
                  {t('events.sources.editSource')}
                </Dropdown.Option>
                <Dropdown.Option onClick={() => this.setState({deleting: source})}>
                  {t('common.remove')}
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
          deleteText={t('common.remove')}
          entity={deleting}
          deleteEntity={this.removeSource}
          onClose={() => this.setState({deleting: null})}
          descriptionText={t('events.sources.deleteWarning')}
        />
        {editing && (
          <EventsSourceModal
            initialSource={editing}
            existingSources={sources}
            onConfirm={sources => {
              this.props.onChange(sources);
              this.closeSourceModal();
            }}
            onClose={this.closeSourceModal}
          />
        )}
      </div>
    );
  }
}
