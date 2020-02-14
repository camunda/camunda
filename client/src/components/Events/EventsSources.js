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
  removeSource = ({processDefinitionKey, type}) => {
    let filter;
    if (type === 'external') {
      filter = src => src.type !== 'external';
    } else {
      filter = src => src.processDefinitionKey !== processDefinitionKey;
    }

    this.props.onChange(this.props.sources.filter(filter));
  };

  render() {
    const {editing, deleting} = this.state;
    const {sources} = this.props;

    return (
      <div className="EventsSources">
        <div className="sourcesList">
          {sources.map(source => {
            if (source.type === 'external') {
              return (
                <Dropdown
                  className={classnames({isActive: true})}
                  label={t('events.sources.externalEvents')}
                  key="externalEvents"
                >
                  <Dropdown.Option onClick={() => this.setState({deleting: {type: 'external'}})}>
                    {t('common.remove')}
                  </Dropdown.Option>
                </Dropdown>
              );
            } else {
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
            }
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
