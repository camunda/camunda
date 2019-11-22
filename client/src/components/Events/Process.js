/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Redirect} from 'react-router-dom';

import {LoadingIndicator} from 'components';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {t} from 'translation';

import {loadProcess} from './service';
import ProcessView from './ProcessView';
import ProcessEdit from './ProcessEdit';

import './Process.scss';

export default withErrorHandling(
  class Process extends React.Component {
    state = {
      name: null,
      xml: null,
      mappings: null,
      redirect: null
    };

    componentDidMount() {
      if (this.isNew()) {
        this.initializeNewProcess();
      } else {
        this.loadProcess();
      }
    }

    getId = () => this.props.match.params.id;
    isNew = () => this.getId() === 'new';

    initializeNewProcess = () => {
      const id =
        'Process_' +
        Math.random()
          .toString(36)
          .substr(2);

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
        mappings: {}
      });
    };

    loadProcess = () => {
      this.props.mightFail(
        loadProcess(this.getId()),
        ({name, xml, mappings}) => this.setState({name, xml, mappings}),
        showError
      );
    };

    componentDidUpdate() {
      if (this.state.redirect) {
        this.setState({redirect: null});
      }
    }

    render() {
      const {viewMode} = this.props.match.params;
      const {name, xml, mappings, redirect} = this.state;

      if (redirect) {
        return <Redirect to={redirect} />;
      }

      if (!xml) {
        return <LoadingIndicator />;
      }

      return (
        <div className="Process">
          {viewMode === 'edit' ? (
            <ProcessEdit
              isNew={this.isNew()}
              id={this.getId()}
              initialName={name}
              initialXml={xml}
              initialMappings={mappings}
              onSave={({id, name, xml, mappings}) =>
                this.setState({name, xml, mappings, redirect: `../${id}/`})
              }
            />
          ) : (
            <ProcessView
              id={this.getId()}
              name={name}
              xml={xml}
              mappings={mappings}
              onDelete={() => this.setState({redirect: '../'})}
            />
          )}
        </div>
      );
    }
  }
);
