/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Redirect} from 'react-router-dom';

import {EntityList, Deleter} from 'components';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {t} from 'translation';

import {ReactComponent as ProcessIcon} from './icons/process.svg';

import {loadProcesses, removeProcess} from './service';

export default withErrorHandling(
  class Events extends React.Component {
    state = {
      processes: null,
      deleting: null,
      redirect: null
    };

    componentDidMount() {
      this.loadList();
    }

    loadList = () => {
      this.props.mightFail(loadProcesses(), processes => this.setState({processes}), showError);
    };

    render() {
      const {processes, deleting, redirect} = this.state;

      if (redirect) {
        return <Redirect to={redirect} />;
      }

      return (
        <div className="Events">
          <EntityList
            name={t('navigation.events')}
            empty={t('events.empty')}
            isLoading={!processes}
            data={
              processes &&
              processes.map(process => {
                const {id, name} = process;

                const link = `/events/${id}/`;

                return {
                  icon: <ProcessIcon />,
                  type: t('events.label'),
                  name,
                  link,
                  actions: [
                    {
                      icon: 'edit',
                      text: t('common.edit'),
                      action: () => this.setState({redirect: link + 'edit'})
                    },
                    {
                      icon: 'delete',
                      text: t('common.delete'),
                      action: () => this.setState({deleting: process})
                    }
                  ]
                };
              })
            }
          />
          <Deleter
            entity={deleting}
            onDelete={this.loadList}
            onClose={() => this.setState({deleting: null})}
            deleteEntity={({id}) => removeProcess(id)}
          />
        </div>
      );
    }
  }
);
