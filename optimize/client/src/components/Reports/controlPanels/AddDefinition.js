/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect} from 'react';
import {withRouter} from 'react-router-dom';
import {Button, InlineNotification} from '@carbon/react';
import {Add} from '@carbon/icons-react';

import {Modal, Checklist} from 'components';
import {useErrorHandling} from 'hooks';
import {getCollection, getRandomId, loadDefinitions} from 'services';
import {t} from 'translation';
import {showError} from 'notifications';
import {getMaxNumDataSourcesForReport} from 'config';

import {loadTenants} from './service';

import './AddDefinition.scss';

export function AddDefinition({location, definitions, type, onAdd}) {
  const [reportDataSourceLimit, setReportDataSourceLimit] = useState(100);
  const [availableDefinitions, setAvailableDefinitions] = useState([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [selectedDefinitions, setSelectedDefinitions] = useState([]);
  const {mightFail} = useErrorHandling();

  const collection = getCollection(location.pathname);
  const isDefinitionLimitReached =
    selectedDefinitions.length + definitions.length > reportDataSourceLimit;

  useEffect(() => {
    (async () => setReportDataSourceLimit(await getMaxNumDataSourcesForReport()))();
  }, []);

  useEffect(() => {
    mightFail(
      loadDefinitions(type, collection),
      (definitions) => setAvailableDefinitions(definitions),
      showError
    );
  }, [mightFail, collection, type]);

  function formatDefinitionLabel(key, name) {
    if (!name) {
      return key;
    }

    const isNameUnique =
      availableDefinitions.filter((definition) => definition.name === name).length === 1;

    return isNameUnique ? name : `${name} (${key})`;
  }

  const handleModalClose = (evt) => {
    evt.stopPropagation();
    setModalOpen(false);
  };

  return (
    <>
      <Button
        kind="tertiary"
        className="AddDefinition cds--layout--size-xs"
        onClick={(evt) => {
          evt.stopPropagation();
          setSelectedDefinitions([]);
          setModalOpen(true);
        }}
        disabled={definitions.length >= reportDataSourceLimit}
        renderIcon={Add}
      >
        {t('common.add')}
      </Button>

      <Modal open={modalOpen} onClose={handleModalClose} className="AddDefinitionModal">
        <Modal.Header title={t('report.definition.add', {type: t('report.definition.' + type)})} />
        <Modal.Content>
          {isDefinitionLimitReached && (
            <InlineNotification
              hideCloseButton
              kind="warning"
              subtitle={t('common.definitionSelection.limitReached', {
                maxNumProcesses: reportDataSourceLimit,
              })}
              className="definitionLimitReachedWarning"
            />
          )}
          <Checklist
            hideSelectAllInView
            allItems={availableDefinitions}
            selectedItems={selectedDefinitions}
            onChange={setSelectedDefinitions}
            customHeader={t(`report.definition.${type}-plural`)}
            formatter={() =>
              availableDefinitions.map(({key, name}) => {
                const hasDefinition = (definition) => definition.key === key;
                return {
                  id: key,
                  label: formatDefinitionLabel(key, name),
                  checked:
                    selectedDefinitions.some(hasDefinition) || definitions.some(hasDefinition),
                  disabled:
                    definitions.some(hasDefinition) ||
                    (isDefinitionLimitReached && !selectedDefinitions.some(hasDefinition)),
                };
              })
            }
            labels={{
              search: t('report.definition.search', {type: t(`common.${type}.label`)}),
              empty: t('common.definitionSelection.noDefinition'),
            }}
          />
        </Modal.Content>
        <Modal.Footer>
          <Button kind="secondary" className="cancel" onClick={handleModalClose}>
            {t('common.cancel')}
          </Button>
          <Button
            className="confirm"
            onClick={(evt) => {
              handleModalClose(evt);
              mightFail(
                loadTenants(
                  type,
                  selectedDefinitions.map(({key}) => ({
                    key,
                    versions: ['all'],
                  })),
                  collection
                ),
                (tenantInfo) =>
                  onAdd(
                    selectedDefinitions.map(({key, name}, idx) => ({
                      key,
                      name,
                      displayName: name,
                      versions: ['all'],
                      tenantIds: tenantInfo[idx].tenants.map(({id}) => id),
                      identifier: getRandomId(),
                    }))
                  ),
                showError
              );
            }}
            disabled={selectedDefinitions.length === 0 || isDefinitionLimitReached}
          >
            {t('common.add')}
          </Button>
        </Modal.Footer>
      </Modal>
    </>
  );
}

export default withRouter(AddDefinition);
