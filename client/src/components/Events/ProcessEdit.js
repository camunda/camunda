/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import update from 'immutability-helper';
import deepEqual from 'fast-deep-equal';

import {EntityNameForm, BPMNDiagram, LoadingIndicator} from 'components';
import {withErrorHandling, withUser} from 'HOC';
import {showError} from 'notifications';
import {nowDirty, nowPristine} from 'saveGuard';
import {t} from 'translation';
import {
  createProcess,
  updateProcess,
  loadProcess,
  getCleanedMappings,
  isNonTimerEvent,
} from './service';
import ProcessRenderer from './ProcessRenderer';
import EventTable from './EventTable';

import './ProcessEdit.scss';

const asMapping = ({group, source, eventName, eventLabel}) => ({
  group,
  source,
  eventName,
  eventLabel,
});

export class ProcessEdit extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      name: null,
      xml: null,
      selectedNode: null,
      mappings: {},
      eventSources: null,
      selectedEvent: null,
    };
  }

  isNew = () => this.props.id === 'new';

  componentDidMount() {
    if (this.isNew()) {
      this.initializeNewProcess();
    } else {
      this.loadProcess();
    }
  }

  componentDidUpdate(prevProps, prevState) {
    const {selectedNode, mappings} = this.state;
    if (prevState.mappings !== mappings) {
      // we need to update the mappings when converting timer node to a normal event node
      const {end} = mappings[selectedNode?.id] || {};
      if (isNonTimerEvent(selectedNode) && end) {
        this.setMapping(asMapping(end), true, 'start');
      }
    }
  }

  initializeNewProcess = async () => {
    const id = 'Process_' + Math.random().toString(36).substr(2);
    this.setState({
      name: t('events.new'),
      xml: `<?xml version="1.0" encoding="UTF-8"?>
      <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" id="Definitions_15hceqv" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="3.4.1">
        <bpmn:process id="${id}" name="${t('events.new')}" isExecutable="true">
        <bpmn:startEvent id="StartEvent_1" />
        </bpmn:process>
        <bpmndi:BPMNDiagram id="BPMNDiagram_1">
          <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="${id}">
            <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent_1">
              <dc:Bounds x="179" y="99" width="36" height="36" />
            </bpmndi:BPMNShape>
          </bpmndi:BPMNPlane>
        </bpmndi:BPMNDiagram>
      </bpmn:definitions>
      `,
      mappings: {},
      eventSources: [],
    });
  };

  loadProcess = () => {
    this.props.mightFail(
      loadProcess(this.props.id),
      ({name, xml, mappings, eventSources}) =>
        this.setState({
          name,
          xml,
          mappings,
          eventSources,
        }),
      showError
    );
  };

  save = () => {
    return new Promise(async (resolve, reject) => {
      const {mightFail, id} = this.props;
      const {name, mappings, xml, eventSources} = this.state;

      if (this.isNew()) {
        mightFail(createProcess({name, xml, mappings, eventSources}), resolve, (error) =>
          reject(showError(error))
        );
      } else {
        mightFail(
          updateProcess(id, name, xml, mappings, eventSources),
          () => resolve(id),
          (error) => reject(showError(error))
        );
      }
    });
  };

  saveAndGoBack = async () => {
    const id = await this.save();

    nowPristine();

    this.props.onSave(id);
  };

  setDirty = () => {
    nowDirty(t('events.label'), this.save);
  };

  setMapping = (event, mapped, mapAs) => {
    this.setDirty();
    this.setState(({mappings, selectedNode}) => {
      let change;
      if (!mappings[selectedNode.id]) {
        // first time this node is mapped, we set the end
        change = {$set: {start: event, end: null}};
      } else {
        if (mapped) {
          if (mapAs) {
            // we change the mapping of a mapped event
            change = {
              [mapAs === 'end' ? 'start' : 'end']: {
                $set: null,
              },
              [mapAs]: {$set: event},
            };
          } else {
            // we map a new event
            // if we already have an end event, we map as start event
            change = {
              [mappings[selectedNode.id].end ? 'start' : 'end']: {
                $set: event,
              },
            };
          }
        } else {
          const mapping = mappings[selectedNode.id];

          const eventType = deepEqual(mapping.start, event) ? 'start' : 'end';
          const otherType = eventType === 'start' ? 'end' : 'start';

          if (mapping[otherType]) {
            // if we have another mapping for the node, just clear start or end
            change = {
              [eventType]: {
                $set: null,
              },
            };
          } else {
            // if there is no other mapping for the node, remove the node from the mappings array
            return {
              mappings: update(mappings, {$unset: [selectedNode.id]}),
            };
          }
        }
      }

      return {
        mappings: update(mappings, {[selectedNode.id]: change}),
      };
    });
  };

  updateSources = (eventSources, reloadMappings) => this.update({eventSources}, reloadMappings);
  updateXml = (xml, reloadMappings) => this.update({xml}, reloadMappings);

  update = (change, reloadMappings) => {
    const {eventSources, mappings, xml} = this.state;
    this.setDirty();
    if (reloadMappings) {
      this.props.mightFail(
        getCleanedMappings({
          eventSources,
          mappings,
          xml,
          ...change,
        }),
        (mappings) => this.setState({mappings, ...change}),
        showError
      );
    } else {
      this.setState(change);
    }
  };

  onSelectNode = ({newSelection}) => {
    let selectedNode = null;
    if (newSelection.length === 1) {
      selectedNode = newSelection[0].businessObject;
    }

    this.setState({selectedEvent: null, selectedNode});
  };

  render() {
    const {name, mappings, selectedNode, xml, eventSources, selectedEvent} = this.state;

    if (!xml) {
      return <LoadingIndicator />;
    }

    return (
      <div className="ProcessEdit">
        <div className="header">
          <EntityNameForm
            name={name}
            isNew={this.isNew()}
            entity="Process"
            onChange={({target}) => {
              this.setDirty();
              this.setState({name: target.value});
            }}
            onSave={this.saveAndGoBack}
            onCancel={nowPristine}
          />
        </div>
        <div className="content">
          <BPMNDiagram xml={xml} allowModeling>
            <ProcessRenderer
              name={name}
              selectedEvent={selectedEvent}
              mappings={mappings}
              onChange={this.updateXml}
              onSelectNode={this.onSelectNode}
            />
          </BPMNDiagram>
        </div>
        <EventTable
          selection={selectedNode}
          mappings={mappings}
          eventSources={eventSources}
          xml={xml}
          onMappingChange={this.setMapping}
          onSourcesChange={this.updateSources}
          onSelectEvent={(selectedEvent) => this.setState({selectedEvent})}
        />
      </div>
    );
  }
}

export default withErrorHandling(withUser(ProcessEdit));
