/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect, useRef} from 'react';
import {Link} from 'react-router-dom';
import classnames from 'classnames';
import deepEqual from 'fast-deep-equal';

import {Button, Modal, DefinitionSelection, BPMNDiagram, DiagramScrollLock} from 'components';
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
  const [xmlData, setXmlData] = useState([]);
  const [template, setTemplate] = useState();
  const [selectedDefinitions, setSelectedDefinitions] = useState([]);
  const diagramArea = useRef();

  useEffect(() => {
    if (selectedDefinitions.length === 0) {
      return setXmlData([]);
    }

    (async () => {
      const newXmlData = await Promise.all(
        selectedDefinitions.map(({key, name, versions, tenantIds: tenants}) => {
          return (
            xmlData.find(
              (definition) => definition.key === key && deepEqual(versions, definition.versions)
            ) ||
            new Promise((resolve, reject) => {
              mightFail(
                loadProcessDefinitionXml(key, versions[0], tenants[0]),
                (xml) => resolve({key, name, versions, xml}),
                (error) => reject(showError(error))
              );
            })
          );
        })
      );

      setXmlData(newXmlData);
    })();

    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedDefinitions, mightFail]);

  const validSelection =
    name && ((xmlData.length > 0 && selectedDefinitions.length > 0) || !template);

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
              selectedDefinitions={selectedDefinitions}
              onChange={setSelectedDefinitions}
            />
          </div>

          <div className="diagramArea" ref={diagramArea}>
            {xmlData.map(({xml, key, name}, idx) => (
              <div
                key={xmlData.length + idx}
                style={{
                  height:
                    getDiagramHeight(xmlData.length, diagramArea.current?.offsetHeight) + 'px',
                }}
                className="diagramContainer"
              >
                <div className="title">{name || key}</div>
                <BPMNDiagram xml={xml} emptyText={t('templates.noXmlHint')} />
                <DiagramScrollLock />
              </div>
            ))}
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
              definitions: selectedDefinitions,
              xml: xmlData[0]?.xml,
            }),
          }}
        >
          {t(entity + '.create')}
        </Link>
      </Modal.Actions>
    </Modal>
  );
}

function getDiagramHeight(count, fullHeight) {
  if (count === 1) {
    return fullHeight;
  }

  if (count === 2) {
    return 0.5 * fullHeight;
  }

  return 0.425 * fullHeight;
}

export default withErrorHandling(TemplateModal);
