/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';
import {Link} from 'react-router-dom';
import classnames from 'classnames';
import deepEqual from 'deep-equal';

import {Button, LabeledInput, Modal, Form, DefinitionSelection, BPMNDiagram} from 'components';
import {loadProcessDefinitionXml} from 'services';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import heatmapImg from './images/heatmap.jpg';
import durationImg from './images/duration.svg';
import tableImg from './images/table.svg';
import chartImg from './images/chart.svg';

import './ReportTemplateModal.scss';

const templates = [
  {name: 'blank'},
  {
    name: 'heatmap',
    img: heatmapImg,
    config: {
      view: {entity: 'flowNode', property: 'frequency'},
      groupBy: {type: 'flowNodes', value: null},
      visualization: 'heat',
    },
  },
  {
    name: 'number',
    img: durationImg,
    config: {
      view: {entity: 'processInstance', property: 'duration'},
      groupBy: {type: 'none', value: null},
      visualization: 'number',
    },
  },
  {
    name: 'table',
    img: tableImg,
    config: {
      view: {entity: 'userTask', property: 'frequency'},
      groupBy: {type: 'userTasks', value: null},
      visualization: 'table',
    },
  },
  {
    name: 'chart',
    img: chartImg,
    config: {
      view: {entity: 'processInstance', property: 'frequency'},
      groupBy: {type: 'startDate', value: {unit: 'automatic'}},
      visualization: 'bar',
    },
  },
];

export function ReportTemplateModal({onClose, mightFail}) {
  const [name, setName] = useState(t('report.new'));
  const [definition, setDefinition] = useState({definitionKey: '', versions: [], tenants: []});
  const [xml, setXml] = useState();
  const [template, setTemplate] = useState();

  const {definitionKey, versions, tenants} = definition;

  useEffect(() => {
    const {definitionKey, versions, tenants} = definition;
    if (definitionKey && versions?.length && tenants?.length) {
      mightFail(
        loadProcessDefinitionXml(definitionKey, versions[0], tenants[0]),
        setXml,
        showError
      );
    } else {
      setXml();
    }
  }, [definition, mightFail]);

  return (
    <Modal open size="max" onClose={onClose} className="ReportTemplateModal">
      <Modal.Header>{t('report.createNew')}</Modal.Header>
      <Modal.Content>
        <div className="definitionSelection">
          <div className="formArea">
            <Form>
              <Form.Group>
                <LabeledInput
                  type="text"
                  label={t('report.addName')}
                  value={name}
                  onChange={({target: {value}}) => setName(value)}
                  autoComplete="off"
                />
              </Form.Group>
            </Form>
            <DefinitionSelection
              type="process"
              expanded
              definitionKey={definitionKey}
              versions={versions}
              tenants={tenants}
              onChange={({key, versions, tenantIds}) =>
                setDefinition({definitionKey: key, versions, tenants: tenantIds})
              }
            />
          </div>
          <div className="diagramArea">
            <BPMNDiagram xml={xml} />
          </div>
        </div>
        <div className="configurationSelection">
          <div className="templateContainer">
            {templates.map(({name, img, config}, idx) => (
              <Button
                key={idx}
                className={classnames({active: deepEqual(template, config)})}
                onClick={() => setTemplate(config)}
              >
                {img ? (
                  <img src={img} alt={t('report.templates.' + name)} />
                ) : (
                  <div className="imgPlaceholder" />
                )}
                <div className="name">{t('report.templates.' + name)}</div>
              </Button>
            ))}
          </div>
        </div>
      </Modal.Content>
      <Modal.Actions>
        <Button main className="cancel" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Link
          className="Button main primary confirm"
          disabled={!name || !xml || !definitionKey}
          to={{
            pathname: 'report/new/edit',
            state: {
              name,
              data: {
                configuration: {xml},
                processDefinitionKey: definitionKey,
                processDefinitionVersions: versions,
                tenantIds: tenants,
                ...(template || {}),
              },
            },
          }}
        >
          {t('report.create')}
        </Link>
      </Modal.Actions>
    </Modal>
  );
}

export default withErrorHandling(ReportTemplateModal);
