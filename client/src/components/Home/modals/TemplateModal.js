/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect} from 'react';
import {Link} from 'react-router-dom';
import classnames from 'classnames';
import deepEqual from 'deep-equal';

import {Button, Modal, DefinitionSelection, BPMNDiagram} from 'components';
import {loadProcessDefinitionXml} from 'services';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import './TemplateModal.scss';

export function TemplateModal({
  onClose,
  mightFail,
  templates,
  entity,
  className,
  templateToState = (data) => data,
}) {
  const [name, setName] = useState(t(entity + '.new'));
  const [definition, setDefinition] = useState({definitionKey: '', versions: [], tenants: []});
  const [xml, setXml] = useState();
  const [template, setTemplate] = useState();

  const {definitionKey, definitionName, versions, tenants} = definition;

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

  const validSelection = name && ((xml && definitionKey) || !template);

  return (
    <Modal
      open
      size="max"
      onClose={onClose}
      className={classnames('TemplateModal', className, {noProcess: !template})}
    >
      <Modal.Header>{t(entity + '.createNew')}</Modal.Header>
      <Modal.Content>
        <div className="configurationSelection">
          <div className="templateContainer">
            {templates.map(({name, hasSubtitle, img, config}, idx) => (
              <Button
                key={idx}
                className={classnames({active: deepEqual(template, config), hasSubtitle})}
                onClick={() => {
                  setTemplate(config);
                  setName(t(entity + '.templates.' + name));
                }}
              >
                {img ? (
                  <img src={img} alt={t(entity + '.templates.' + name)} />
                ) : (
                  <div className="imgPlaceholder" />
                )}
                <div className="name">{t(entity + '.templates.' + name)}</div>
                {hasSubtitle && (
                  <div className="subTitle">{t(entity + '.templates.' + name + '_subTitle')}</div>
                )}
              </Button>
            ))}
          </div>
        </div>
        <div className="definitionSelection">
          <div className="formArea">
            <DefinitionSelection
              type="process"
              expanded
              definitionKey={definitionKey}
              versions={versions}
              tenants={tenants}
              onChange={({key, versions, tenantIds, name}) =>
                setDefinition({
                  definitionKey: key,
                  versions,
                  tenants: tenantIds,
                  definitionName: name,
                })
              }
            />
          </div>
          <div className="diagramArea">
            <BPMNDiagram xml={xml} emptyText={t('templates.noXmlHint')} />
          </div>
          {!template && <div className="noProcessHint">{t('templates.noProcessHint')}</div>}
        </div>
      </Modal.Content>
      <Modal.Actions>
        <Button main className="cancel" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Link
          className="Button main primary confirm"
          disabled={!validSelection}
          to={{
            pathname: entity + '/new/edit',
            state: templateToState({
              name,
              template,
              definitionKey,
              versions,
              definitionName,
              tenants,
              xml,
            }),
          }}
        >
          {t(entity + '.create')}
        </Link>
      </Modal.Actions>
    </Modal>
  );
}

export default withErrorHandling(TemplateModal);
