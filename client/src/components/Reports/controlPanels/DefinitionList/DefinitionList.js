/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useState} from 'react';
import {withRouter} from 'react-router-dom';
import classnames from 'classnames';
import useDeepCompareEffect from 'use-deep-compare-effect';
import deepEqual from 'fast-deep-equal';

import {Button, Icon, Popover} from 'components';
import {withErrorHandling} from 'HOC';
import {getCollection} from 'services';
import {t} from 'translation';
import {showError} from 'notifications';

import {loadTenants} from './service';
import DefinitionEditor from './DefinitionEditor';

import './DefinitionList.scss';

export function DefinitionList({mightFail, location, definitions = [], type, onChange, onRemove}) {
  const [openPopover, setOpenPopover] = useState();
  const [tenantInfo, setTenantInfo] = useState();

  const collection = getCollection(location.pathname);
  const definitionKeysAndVersions = definitions.map(({key, versions}) => ({key, versions}));

  useDeepCompareEffect(() => {
    mightFail(loadTenants(type, definitionKeysAndVersions, collection), setTenantInfo, showError);
  }, [definitionKeysAndVersions, collection, mightFail, type]);

  function getTenantInfoForDefinition(definition) {
    return tenantInfo?.find(
      ({key, versions}) => key === definition.key && deepEqual(versions, definition.versions)
    )?.tenants;
  }

  if (definitions.length === 0) {
    return <div className="DefinitionList empty">{t('report.noSources')}</div>;
  }

  return (
    <ul className="DefinitionList">
      {definitions.map((definition, idx) => {
        const tenantInfo = getTenantInfoForDefinition(definition);
        const tenants = definition.tenantIds.map(
          (tenantId) => tenantInfo?.find(({id}) => id === tenantId).name ?? tenantId
        );

        return (
          <li key={idx + definition.key} className={classnames({active: openPopover === idx})}>
            <h4>{definition.displayName || definition.name}</h4>
            <div className="info">
              {t('common.definitionSelection.version.label')}:{' '}
              {getVersionString(definition.versions)}
            </div>
            {tenantInfo?.length > 1 && (
              <div className="info">
                {t('common.tenant.label')}: {tenants.join(', ') || t('common.none')}
              </div>
            )}
            <div className="actions">
              <Popover
                renderInPortal="DefinitionList"
                onOpen={() => setOpenPopover(idx)}
                onClose={() => setOpenPopover()}
                title={<Icon type="edit-small" size="14px" />}
                tabIndex="0"
              >
                <DefinitionEditor
                  collection={collection}
                  definition={definition}
                  tenantInfo={tenantInfo}
                  onChange={(change) => onChange(change, idx)}
                  onRemove={() => {
                    onRemove(idx);
                    setOpenPopover();
                  }}
                  type={type}
                />
              </Popover>
              <Button icon onClick={() => onRemove(idx)}>
                <Icon type="close-small" size="14px" />
              </Button>
            </div>
          </li>
        );
      })}
    </ul>
  );
}

function getVersionString(versions) {
  if (versions.length === 1 && versions[0] === 'all') {
    return t('common.all');
  } else if (versions.length === 1 && versions[0] === 'latest') {
    return t('common.definitionSelection.latest');
  } else if (versions.length) {
    return versions.join(', ');
  }

  return t('common.none');
}

export default withRouter(withErrorHandling(DefinitionList));
