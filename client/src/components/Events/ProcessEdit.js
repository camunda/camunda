/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import update from 'immutability-helper';
import deepEqual from 'deep-equal';

import {EntityNameForm, BPMNDiagram} from 'components';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {nowDirty, nowPristine} from 'saveGuard';
import {t} from 'translation';

import {createProcess, updateProcess} from './service';
import ProcessRenderer from './ProcessRenderer';
import EventTable from './EventTable';

import './ProcessEdit.scss';

export default withErrorHandling(
  class ProcessEdit extends React.Component {
    getXml = {};

    constructor(props) {
      super(props);

      this.state = {
        name: props.initialName,
        selectedNode: null,
        mappings: props.initialMappings
      };
    }

    save = () => {
      return new Promise(async (resolve, reject) => {
        const {isNew, mightFail, id} = this.props;
        const {name, mappings} = this.state;
        const xml = await this.getXml.action();

        if (isNew) {
          mightFail(
            createProcess(name, xml, mappings),
            id => resolve({id, name, xml, mappings}),
            error => reject(showError(error))
          );
        } else {
          mightFail(
            updateProcess(id, name, xml, mappings),
            () => resolve({id, name, xml, mappings}),
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

    setMapping = (event, mapped, mapAs) => {
      this.setState(({mappings, selectedNode}) => {
        let change;
        if (!mappings[selectedNode.id]) {
          // first time this node is mapped, we set the end
          change = {$set: {start: null, end: event}};
        } else {
          if (mapped) {
            if (mapAs) {
              // we change the mapping of a mapped event
              change = {
                [mapAs === 'end' ? 'start' : 'end']: {
                  $set: null
                },
                [mapAs]: {$set: event}
              };
            } else {
              // we map a new event
              // if we already have an end event, we map as start event
              change = {
                [mappings[selectedNode.id].end ? 'start' : 'end']: {
                  $set: event
                }
              };
            }
          } else {
            // unset the mapping for the one that matches the provided event
            change = {
              [deepEqual(mappings[selectedNode.id].start, event) ? 'start' : 'end']: {
                $set: null
              }
            };
          }
        }

        return {
          mappings: update(mappings, {[selectedNode.id]: change})
        };
      });
    };

    render() {
      const {name, mappings, selectedNode} = this.state;
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
            <ProcessRenderer
              name={name}
              mappings={mappings}
              getXml={this.getXml}
              onChange={this.setDirty}
              onSelectNode={({newSelection}) => {
                if (newSelection.length !== 1) {
                  this.setState({selectedNode: null});
                } else {
                  this.setState({selectedNode: newSelection[0].businessObject});
                }
              }}
              onElementDelete={({context: {elements}}) =>
                this.setState(({mappings}) => ({
                  mappings: update(mappings, {
                    $unset: elements.map(({businessObject: {id}}) => id)
                  })
                }))
              }
            />
          </BPMNDiagram>
          <EventTable selection={selectedNode} mappings={mappings} onChange={this.setMapping} />
        </div>
      );
    }
  }
);
