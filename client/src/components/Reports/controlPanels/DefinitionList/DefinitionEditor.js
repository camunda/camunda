/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';

import {
  Button as LegacyButton,
  Icon,
  Labeled,
  Input,
  BPMNDiagram,
  VersionPopover,
  TenantPopover,
} from 'components';
import {withErrorHandling} from 'HOC';
import {loadProcessDefinitionXml} from 'services';
import {t} from 'translation';
import {showError} from 'notifications';

import {loadTenants, loadVersions} from './service';
import RenameVariablesModal from './RenameVariablesModal';
import DiagramModal from './DiagramModal';

import './DefinitionEditor.scss';

export function DefinitionEditor({mightFail, collection, type, definition, tenantInfo, onChange}) {
  const {key, versions, tenantIds} = definition;
  const firstVersion = versions[0];
  const firstTenant = tenantIds[0];

  const [availableVersions, setAvailableVersions] = useState([]);
  const [selectedSpecificVersions, setSelectedSpecificVersions] = useState(
    isSpecificVersion(versions) ? versions : []
  );
  const [availableTenants, setAvailableTenants] = useState(versions.length ? tenantInfo : []);
  const [xml, setXml] = useState();
  const [loadingXml, setLoadingXml] = useState(true);
  const [loadingVersions, setLoadingVersions] = useState(false);
  const [loadingTenants, setLoadingTenants] = useState(false);
  const [displayName, setDisplayName] = useState(definition.displayName);
  const [diagramModalOpen, setDiagramModalOpen] = useState(false);
  const [variableModalOpen, setVariableModalOpen] = useState(false);

  useEffect(() => {
    mightFail(loadVersions(type, collection, key), setAvailableVersions, showError);
  }, [mightFail, collection, key, type]);

  useEffect(() => {
    setLoadingXml(true);
    mightFail(
      loadProcessDefinitionXml(key, firstVersion, firstTenant),
      (xml) => {
        setXml(xml);
        setLoadingXml(false);
      },
      showError
    );
  }, [mightFail, key, firstVersion, firstTenant]);

  const handleModalClose = (evt) => {
    evt.stopPropagation();
    setDiagramModalOpen(false);
  };

  return (
    <>
      <div className="DefinitionEditor">
        <div className="definitionLabel">{t('report.definition.' + type)}</div>
        <div className="definitionName">{definition.name}</div>
        <div className="selectionPanel">
          <div className="version entry">
            <Labeled label={t('common.definitionSelection.version.label')} />
            <VersionPopover
              versions={availableVersions}
              selected={definition.versions}
              selectedSpecificVersions={selectedSpecificVersions}
              loading={loadingVersions}
              onChange={async (newVersions) => {
                setLoadingVersions(true);
                if (isSpecificVersion(newVersions)) {
                  setSelectedSpecificVersions(newVersions);
                }

                if (!newVersions.length) {
                  setAvailableTenants([]);
                  await onChange({...definition, versions: newVersions, tenantIds: []});
                  setLoadingVersions(false);
                } else {
                  mightFail(
                    loadTenants(type, [{key: definition.key, version: newVersions}], collection),
                    async ([{tenants: newAvailableTenants}]) => {
                      const prevTenants = availableTenants;
                      const deselectedTenants = prevTenants
                        ?.map(({id}) => id)
                        .filter((tenant) => !definition.tenantIds?.includes(tenant));
                      const tenantIds = newAvailableTenants
                        ?.map(({id}) => id)
                        .filter((tenant) => !deselectedTenants?.includes(tenant));

                      setAvailableTenants(newAvailableTenants);
                      await onChange({...definition, versions: newVersions, tenantIds});
                      setLoadingVersions(false);
                    },
                    showError
                  );
                }
              }}
            />
          </div>
          {availableTenants?.length > 1 && (
            <div className="tenant entry">
              <Labeled label={t('common.tenant.label')} />
              <TenantPopover
                tenants={availableTenants}
                selected={definition.tenantIds}
                loading={loadingTenants}
                onChange={async (newTenants) => {
                  setLoadingTenants(true);
                  await onChange({...definition, tenantIds: newTenants});
                  setLoadingTenants(false);
                }}
              />
            </div>
          )}
          <div className="displayName">
            <Labeled label={t('report.displayName')} />
            <Input
              placeholder={t('report.displayNamePlaceholder')}
              value={displayName}
              onChange={(evt) => setDisplayName(evt.target.value)}
              onBlur={(evt) => {
                // This input field is inside a Popover. When the user click outside of the Popover, we want to close it.
                // Popovers close, when they receive a click event outside of the Popover. However, click events are
                // only triggered after the mouseup, while the blur event fires after mousedown. The onChange handler
                // we call here causes a react state update that rerenders the ReportRenderer. So if a user clicks on
                // the ReportRenderer to close the popover, we get a blur event that causes the original target of the click to
                // disappear because of the rerender, so that the click event is never generated and the Popover stays open.
                //
                // To fix that, we need to identify that the blur event was caused by a click rather than a key event (1) and
                // then delay the execution of the onChange update until after the click has been processed (2). (1) can be
                // handled with the relatedTarget property of the event. For keyboard blur events this is always set to the new
                // element to receive focus. Some mouse events can also set this, if the clicked element is focusable. But as the
                // ReportRenderer does not have any focusable elements, we can use this to identify a click in the ReportRenderer
                // For (2), we register a one time mouseup event listener on the body. As the click handler fires after mouseup,
                // we further delay the execution of onChange using setTimeout to give the Popover time to process the click event.
                if (!evt.relatedTarget) {
                  document.body.addEventListener(
                    'mouseup',
                    () => setTimeout(() => onChange({...definition, displayName})),
                    {once: true}
                  );
                } else {
                  onChange({...definition, displayName});
                }
              }}
            />
          </div>
        </div>
        <div className="diagram">
          <BPMNDiagram xml={xml} disableNavigation loading={loadingXml} />
          <LegacyButton
            small
            disabled={loadingXml || !xml}
            onClick={() => setDiagramModalOpen(true)}
          >
            <Icon type="fullscreen" />
            {t('common.entity.viewModel.model')}
          </LegacyButton>
        </div>
        <div className="actionBar">
          <LegacyButton
            small
            onClick={() => {
              setVariableModalOpen(true);
            }}
          >
            <Icon type="edit" />
            {t('report.definition.variables.rename')}
          </LegacyButton>
        </div>
      </div>
      <RenameVariablesModal
        open={variableModalOpen}
        definitionKey={definition.key}
        availableTenants={tenantInfo?.map(({id}) => id)}
        onChange={() => onChange(definition)}
        onClose={() => {
          setVariableModalOpen(false);
        }}
      />
      <DiagramModal
        open={diagramModalOpen}
        onClose={handleModalClose}
        xml={xml}
        definitionName={definition.name}
      />
    </>
  );
}

function isSpecificVersion(versions) {
  return versions && versions[0] !== 'latest' && versions[0] !== 'all';
}

export default withErrorHandling(DefinitionEditor);
