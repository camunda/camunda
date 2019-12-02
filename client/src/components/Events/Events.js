/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Redirect} from 'react-router-dom';

import {EntityList, Deleter, Dropdown} from 'components';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {t} from 'translation';

import {ReactComponent as ProcessIcon} from './icons/process.svg';

import {loadProcesses, createProcess, removeProcess} from './service';

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

    upload = () => {
      const el = document.createElement('input');
      el.type = 'file';
      el.accept = '.bpmn';

      el.addEventListener('change', () => {
        const reader = new FileReader();

        reader.addEventListener('load', () => {
          const xml = reader.result;

          try {
            // get the process name
            const parser = new DOMParser();
            const process = parser
              .parseFromString(xml, 'text/xml')
              .getElementsByTagName('bpmn:process')[0];
            const name = process.getAttribute('name') || process.getAttribute('id');

            this.props.mightFail(createProcess(name, xml, {}), this.loadList, showError);
          } catch (e) {
            showError(t('events.parseError'));
          }
        });
        reader.readAsText(el.files[0]);
      });

      el.click();
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
            action={
              <Dropdown label={t('events.new')}>
                <Dropdown.Option link="new/edit">{t('events.modelProcess')}</Dropdown.Option>
                <Dropdown.Option onClick={this.upload}>{t('events.upload')}</Dropdown.Option>
              </Dropdown>
            }
            data={
              processes &&
              processes.map(process => {
                const {id, name} = process;

                const link = `/eventBasedProcess/${id}/`;

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
            type="process"
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
