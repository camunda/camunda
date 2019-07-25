/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Button, Icon, CollectionsDropdown, Badge} from 'components';
import {getReportInfo, getReportIcon} from '../service';
import LastModified from './LastModified';
import {Link} from 'react-router-dom';
import {withStore} from '../OverviewStore';
import {t} from 'translation';

export default withStore(function ReportItem({
  store: {collections},
  report,
  duplicateEntity,
  showDeleteModalFor,
  collection,
  entitiesCollections,
  toggleEntityCollection,
  setCollectionToUpdate
}) {
  const {Icon: ReportIcon, label} = getReportIcon(report);
  const reportCollections = entitiesCollections[report.id];
  return (
    <li className="ReportItem listItem">
      <Link className="info" to={`/report/${report.id}`}>
        <span className="icon" title={label}>
          <ReportIcon />
        </span>
        <div className="textInfo">
          <div className="data dataTitle">
            <h3>{report.name}</h3>
            {report.combined && <Badge>{t('home.report.badge.combined')}</Badge>}
            {report.reportType && report.reportType === 'decision' && (
              <Badge>{t('home.report.badge.decision')}</Badge>
            )}
          </div>
          <div className="extraInfo">
            <span className="data custom">{getReportInfo(report)}</span>
            <LastModified
              label={t('common.entity.changed')}
              date={report.lastModified}
              author={report.lastModifier}
            />
          </div>
        </div>
      </Link>
      <div className="collections">
        <CollectionsDropdown
          entity={report}
          currentCollection={collection}
          toggleEntityCollection={toggleEntityCollection}
          setCollectionToUpdate={setCollectionToUpdate}
          collections={collections}
          entityCollections={reportCollections}
        />
      </div>
      <div className="operations">
        <Link title={t('home.report.edit')} to={`/report/${report.id}/edit`}>
          <Icon type="edit" className="editLink" />
        </Link>
        <Button
          title={t('home.report.duplicate')}
          onClick={duplicateEntity('report', report, collection)}
        >
          <Icon type="copy-document" className="duplicateIcon" />
        </Button>
        <Button
          title={t('home.report.delete')}
          onClick={showDeleteModalFor({type: 'report', entity: report})}
        >
          <Icon type="delete" className="deleteIcon" />
        </Button>
      </div>
    </li>
  );
});
