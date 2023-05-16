/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useEffect, useRef} from 'react';
import {Link} from 'react-router-dom';
import classnames from 'classnames';
import deepEqual from 'fast-deep-equal';
import {Button, Column, Grid} from '@carbon/react';

import {
  Modal,
  DefinitionSelection,
  BPMNDiagram,
  DiagramScrollLock,
  Tooltip,
  Button as LegacyButton,
} from 'components';
import {loadProcessDefinitionXml} from 'services';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import './TemplateModal.scss';

export function TemplateModal({
  onClose,
  mightFail,
  templateGroups,
  entity,
  className,
  blankSlate,
  templateToState = (data) => data,
  onConfirm,
  initialDefinitions = [],
}) {
  const firstTemplate = templateGroups[1].templates[0];
  const [name, setName] = useState(t(entity + '.templates.' + firstTemplate.name));
  const [description, setDescription] = useState(firstTemplate.description);
  const [xmlData, setXmlData] = useState([]);
  const [template, setTemplate] = useState(firstTemplate.config);
  const [selectedDefinitions, setSelectedDefinitions] = useState(initialDefinitions);
  const diagramArea = useRef();
  const templateContainer = useRef();

  // load the xml for the selected definitions
  useEffect(() => {
    if (selectedDefinitions.length === 0) {
      return setXmlData([]);
    }

    (async () => {
      const newXmlData = await Promise.all(
        selectedDefinitions.map(({key, name, versions, tenantIds: tenants}) => {
          return (
            xmlData.find(
              (definition) =>
                definition.key === key &&
                deepEqual(versions, definition.versions) &&
                deepEqual(tenants, definition.tenants)
            ) ||
            new Promise((resolve, reject) => {
              mightFail(
                loadProcessDefinitionXml(key, versions[0], tenants[0]),
                (xml) => resolve({key, name, versions, tenants, xml}),
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

  // if the selected element gets disabled, select the next enabled element
  useEffect(() => {
    const templates = templateGroups.map(({templates}) => templates).flat();
    const currentlySelectedTemplate = templates.find(({config}) => deepEqual(config, template));

    if (
      selectedDefinitions.length > 0 &&
      currentlySelectedTemplate?.disabled?.(selectedDefinitions)
    ) {
      const enabledTemplate = templates.find(
        (template) => !template.disabled?.(selectedDefinitions) && template.name !== 'blank'
      );

      setTemplate(enabledTemplate.config);
      setName(t(entity + '.templates.' + enabledTemplate.name));
      setDescription(enabledTemplate.description);
    }
  }, [templateGroups, selectedDefinitions, template, entity]);

  // scroll to the selected element
  useEffect(() => {
    if (selectedDefinitions.length > 0) {
      const activeElement = templateContainer.current?.querySelector('.active');
      activeElement?.scrollIntoView({block: 'nearest', inline: 'nearest'});
    }
  }, [template, selectedDefinitions]);

  // resize diagram containers on window resize to ensure there are 3 diagrams visible at any time
  useEffect(() => {
    const resizeObserver = new ResizeObserver(() => {
      if (diagramArea.current && selectedDefinitions.length) {
        Array.from(diagramArea.current.children).forEach((child) => {
          child.style.height =
            getDiagramHeight(xmlData.length, diagramArea.current?.clientHeight) + 'px';
        });
      }
    });

    if (diagramArea.current) {
      resizeObserver.observe(diagramArea.current);
    }

    return () => {
      resizeObserver.disconnect();
    };
  }, [xmlData.length, selectedDefinitions.length]);

  const validSelection =
    name && ((xmlData.length > 0 && selectedDefinitions.length > 0) || !template);

  const newEntityState = templateToState({
    name,
    description,
    template,
    definitions: selectedDefinitions.map((def) => ({...def, displayName: def.name})),
    xml: xmlData[0]?.xml,
  });

  return (
    <Modal
      open
      size="lg"
      onClose={onClose}
      className={classnames('TemplateModal', className, {noProcess: !template})}
      isFullWidth
    >
      <Modal.Header>{t(entity + '.createNew')}</Modal.Header>
      <Modal.Content>
        <Grid className="gridContainer">
          <Column sm={3} md={6} lg={11} className="definitionSelection">
            <div className="formArea">
              <DefinitionSelection
                type="process"
                expanded
                selectedDefinitions={selectedDefinitions}
                onChange={setSelectedDefinitions}
                versionTooltip={
                  selectedDefinitions?.length > 1
                    ? t('templates.disabledMessage.editReport')
                    : undefined
                }
              />
            </div>
            <div className="diagramArea" ref={diagramArea}>
              {xmlData.map(({xml, key, name}, idx) => (
                <div key={idx} className="diagramContainer">
                  <div className="title">{name || key}</div>
                  <BPMNDiagram xml={xml} emptyText={t('templates.noXmlHint')} />
                  <DiagramScrollLock />
                </div>
              ))}
              {selectedDefinitions.length === 0 && blankSlate}
            </div>
            {!template && <div className="noProcessHint">{t('templates.noProcessHint')}</div>}
          </Column>
          <Column sm={1} md={2} lg={5} className="configurationSelection">
            <div className="templateContainer" ref={templateContainer}>
              {templateGroups.map(({name, templates}, idx) => (
                <div key={idx} className="group">
                  <div className="groupTitle">{t('templates.templateGroups.' + name)}</div>
                  {templates.map(({name, description, hasSubtitle, img, config, disabled}, idx) => (
                    <Tooltip
                      key={idx}
                      content={
                        disabled?.(selectedDefinitions)
                          ? getDisableStateText(selectedDefinitions)
                          : undefined
                      }
                      position="bottom"
                      align="left"
                    >
                      <div>
                        <LegacyButton
                          className={classnames({
                            active: !disabled?.(selectedDefinitions) && deepEqual(template, config),
                            hasSubtitle,
                          })}
                          onClick={() => {
                            setTemplate(config);
                            setName(t(entity + '.templates.' + name));
                            setDescription(description);
                          }}
                          disabled={disabled?.(selectedDefinitions)}
                        >
                          {img ? (
                            <img src={img} alt={t(entity + '.templates.' + name)} />
                          ) : (
                            <div className="imgPlaceholder" />
                          )}
                          <div className="name">{t(entity + '.templates.' + name)}</div>
                          {hasSubtitle && (
                            <div className="subTitle">
                              {t(entity + '.templates.' + name + '_subTitle')}
                            </div>
                          )}
                        </LegacyButton>
                      </div>
                    </Tooltip>
                  ))}
                </div>
              ))}
            </div>
          </Column>
        </Grid>
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        {onConfirm ? (
          <Button
            disabled={!validSelection}
            className="confirm"
            onClick={() => onConfirm(newEntityState)}
          >
            {t(entity + '.create')}
          </Button>
        ) : (
          <Button
            as={Link}
            disabled={!validSelection}
            to={{
              pathname: entity + '/new/edit',
              state: newEntityState,
            }}
          >
            {t(entity + '.create')}
          </Button>
        )}
      </Modal.Footer>
    </Modal>
  );
}

function getDiagramHeight(count, fullHeight) {
  if (!fullHeight) {
    return;
  }

  if (count === 1) {
    return fullHeight;
  }

  if (count === 2) {
    return 0.5 * fullHeight;
  }

  return 0.425 * fullHeight;
}

function getDisableStateText(selectedDefinitions) {
  if (selectedDefinitions.length === 0) {
    return t('templates.disabledMessage.noProcess');
  }

  if (selectedDefinitions.length === 1) {
    return t('templates.disabledMessage.multipleProcess');
  }

  if (selectedDefinitions.length > 1) {
    return t('templates.disabledMessage.singleProcess');
  }
}

export default withErrorHandling(TemplateModal);
