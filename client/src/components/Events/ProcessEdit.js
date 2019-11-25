/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {EntityNameForm, BPMNDiagram} from 'components';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {nowDirty, nowPristine} from 'saveGuard';
import {t} from 'translation';

import {createProcess, updateProcess} from './service';
import ProcessRenderer from './ProcessRenderer';

import './ProcessEdit.scss';

export default withErrorHandling(
  class ProcessEdit extends React.Component {
    getXml = {};

    constructor(props) {
      super(props);

      this.state = {
        name: props.initialName
      };
    }

    save = () => {
      return new Promise(async (resolve, reject) => {
        const {isNew, mightFail, id} = this.props;
        const {name} = this.state;
        const xml = await this.getXml.action();

        if (isNew) {
          mightFail(
            createProcess(name, xml),
            id => resolve({id, name, xml}),
            error => reject(showError(error))
          );
        } else {
          mightFail(
            updateProcess(id, name, xml),
            () => resolve({id, name, xml}),
            error => reject(showError(error))
          );
        }
      });
    };

    saveAndGoBack = async () => {
      const data = await this.save();

      nowPristine();

      this.props.onSave(data);
    };

    setDirty = () => {
      nowDirty(t('events.label'), this.save);
    };

    render() {
      const {name} = this.state;
      const {initialXml, isNew} = this.props;

      return (
        <div className="ProcessEdit">
          <div className="header">
            <EntityNameForm
              name={name}
              isNew={isNew}
              entity="Process"
              onChange={({target}) => {
                this.setDirty();
                this.setState({name: target.value});
              }}
              onSave={this.saveAndGoBack}
              onCancel={nowPristine}
            />
          </div>
          <BPMNDiagram xml={initialXml} allowModeling>
            <ProcessRenderer name={name} getXml={this.getXml} onChange={this.setDirty} />
          </BPMNDiagram>
        </div>
      );
    }
  }
);
