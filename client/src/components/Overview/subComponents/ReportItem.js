/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Button, Icon, CollectionsDropdown} from 'components';
import {getReportInfo, getReportIcon} from '../service';
import LastModified from './LastModified';
import {Link} from 'react-router-dom';
import {withStore} from '../OverviewStore';

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
            {report.combined && <span>Combined</span>}
            {report.reportType && report.reportType === 'decision' && <span>Decision</span>}
          </div>
          <div className="extraInfo">
            <span className="data custom">{getReportInfo(report)}</span>
            <LastModified
              label="Last changed"
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
        <Link title="Edit Report" to={`/report/${report.id}/edit`}>
          <Icon title="Edit Report" type="edit" className="editLink" />
        </Link>
        <Button title="Duplicate Report" onClick={duplicateEntity('report', report, collection)}>
          <Icon type="copy-document" title="Duplicate Report" className="duplicateIcon" />
        </Button>
        <Button
          title="Delete Report"
          onClick={showDeleteModalFor({type: 'report', entity: report})}
        >
          <Icon type="delete" title="Delete Report" className="deleteIcon" />
        </Button>
      </div>
    </li>
  );
});
