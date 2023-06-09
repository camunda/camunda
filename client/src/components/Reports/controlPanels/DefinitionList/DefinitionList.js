/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {withRouter} from 'react-router-dom';
import classnames from 'classnames';
import useDeepCompareEffect from 'use-deep-compare-effect';
import deepEqual from 'fast-deep-equal';

import {Button, Icon, CarbonPopover, Tooltip} from 'components';
import {withDocs, withErrorHandling} from 'HOC';
import {getCollection, formatters} from 'services';
import {t} from 'translation';
import {showError} from 'notifications';

import {loadTenants} from './service';
import DefinitionEditor from './DefinitionEditor';

import './DefinitionList.scss';

const {formatVersions, formatTenants} = formatters;

export function DefinitionList({
  mightFail,
  location,
  definitions = [],
  type,
  onChange,
  onRemove,
  onCopy,
  docsLink,
}) {
  const [openPopover, setOpenPopover] = useState();
  const [tenantInfo, setTenantInfo] = useState();

  const collection = getCollection(location.pathname);
  const definitionKeysAndVersions = definitions.map(({key, versions}) => ({key, versions}));
  const isDefinitionLimitReached = definitions.length >= 10;

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

        return (
          <li key={idx + definition.key} className={classnames({active: openPopover === idx})}>
            <h4>{definition.displayName || definition.name || definition.key}</h4>
            <div className="info">
              {t('common.definitionSelection.version.label')}: {formatVersions(definition.versions)}
            </div>
            {tenantInfo?.length > 1 && (
              <div className="info">
                {t('common.tenant.label')}: {formatTenants(definition.tenantIds, tenantInfo)}
              </div>
            )}
            <div className="actions">
              {!isDefinitionLimitReached && (
                <Tooltip
                  content={t('report.copyTooltip', {
                    entity: t('common.process.label'),
                    docsLink:
                      docsLink +
                      'components/userguide/additional-features/process-variants-comparison/',
                  })}
                  position="bottom"
                >
                  <Button icon onClick={() => onCopy(idx)}>
                    <Icon type="copy-small" size="14px" />
                  </Button>
                </Tooltip>
              )}
              <CarbonPopover
                className="DefinitionList"
                onOpen={() => setOpenPopover(idx)}
                onClose={() => setOpenPopover()}
                title={<Icon type="edit-small" size="14px" />}
                tabIndex="0"
                tooltip={t('common.editName', {name: t('common.process.label')})}
                tooltipPosition="bottom"
                floating
              >
                <DefinitionEditor
                  collection={collection}
                  definition={definition}
                  tenantInfo={tenantInfo}
                  onChange={(change) => onChange(change, idx)}
                  type={type}
                />
              </CarbonPopover>
              <Tooltip
                content={t('common.removeEntity', {entity: t('common.process.label')})}
                position="bottom"
              >
                <Button icon onClick={() => onRemove(idx)}>
                  <Icon type="close-small" size="14px" />
                </Button>
              </Tooltip>
            </div>
          </li>
        );
      })}
    </ul>
  );
}

export default withRouter(withErrorHandling(withDocs(DefinitionList)));
