/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useEffect} from 'react';
import {withRouter} from 'react-router-dom';
import {Button} from '@carbon/react';

import {Button as LegacyButton, Icon, Modal, Checklist, MessageBox} from 'components';
import {withErrorHandling} from 'HOC';
import {getCollection, getRandomId} from 'services';
import {t} from 'translation';
import {showError} from 'notifications';

import {loadDefinitions, loadTenants} from './service';

import './AddDefinition.scss';

export function AddDefinition({mightFail, location, definitions, type, onAdd}) {
  const [availableDefinitions, setAvailableDefinitions] = useState([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [selectedDefinitions, setSelectedDefinitions] = useState([]);

  const collection = getCollection(location.pathname);
  const isDefinitionLimitReached = selectedDefinitions.length + definitions.length >= 10;

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
      <LegacyButton
        small
        className="AddDefinition"
        onClick={(evt) => {
          evt.stopPropagation();
          setSelectedDefinitions([]);
          setModalOpen(true);
        }}
        disabled={definitions.length >= 10}
      >
        <Icon type="plus" />
        {t('common.add')}
      </LegacyButton>
      <Modal open={modalOpen} onClose={handleModalClose} className="AddDefinitionModal">
        <Modal.Header>
          {t('report.definition.add', {type: t('report.definition.' + type)})}
        </Modal.Header>
        <Modal.Content>
          {isDefinitionLimitReached && (
            <MessageBox type="warning">{t('common.definitionSelection.limitReached')}</MessageBox>
          )}
          <Checklist
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
            disabled={selectedDefinitions.length === 0}
          >
            {t('common.add')}
          </Button>
        </Modal.Footer>
      </Modal>
    </>
  );
}

export default withRouter(withErrorHandling(AddDefinition));
