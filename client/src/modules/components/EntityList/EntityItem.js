import React from 'react';
import moment from 'moment';
import classnames from 'classnames';

import {Icon, Button} from 'components';
import {Link} from 'react-router-dom';
import entityIcons from './entityIcons';
import {formatters} from 'services';

import './EntityItem.css';

export default class EntityItem extends React.Component {
  getEntityIcon = entity => {
    let Icon;
    let label;

    const isValidCombined = this.isValidCombinedReport(entity);
    const iconKey = this.getIconKey(entity, isValidCombined);
    const iconData = entityIcons[this.props.api][iconKey];

    if (isValidCombined) {
      Icon = iconData.CombinedComponent;
      label = `Combined ${iconData.label}`;
    } else {
      Icon = iconData.Component;
      label = iconData.label;
    }

    const EntityIconComponent = label ? (
      <span title={label}>
        <Icon />
      </span>
    ) : (
      <Icon />
    );

    return EntityIconComponent;
  };

  getIconKey = (entity, isValidCombined) => {
    // if combined get the visualization type of the containing reports from the EntityList
    if (isValidCombined) return this.props.getReportVis(entity.data.reportIds[0]);
    if (entity.data && entity.data.visualization) return entity.data.visualization;
    return 'generic';
  };

  isValidCombinedReport = entity => {
    return (
      entity.reportType &&
      entity.reportType === 'combined' &&
      entity.data.reportIds &&
      entity.data.reportIds.length
    );
  };

  formatData = entity => {
    const {name, id, lastModified, lastModifier, shared} = entity;
    const entry = {
      name,
      link: `/${this.props.api}/${id}`,
      icon: this.getEntityIcon(entity),
      infos: [
        {
          parentClassName: 'custom',
          content: this.props.renderCustom && this.props.renderCustom(entity)
        },
        {
          parentClassName: 'dataMeta',
          content: (
            <React.Fragment>
              {`Last modified ${moment(lastModified).format('lll')} by `}
              <strong>{lastModifier}</strong>
            </React.Fragment>
          )
        },
        {
          parentClassName: 'dataIcons',
          content: shared && <Icon type="share" title={`This ${this.props.label} is shared`} />
        }
      ],
      operations: [],
      editData: entity
    };

    if (this.props.operations.includes('edit')) {
      entry.operations.push({
        content: <Icon type="edit" title={`Edit ${this.props.api}`} className="editLink" />,
        link: `/${this.props.api}/${id}/edit`,
        editData: entity,
        parentClassName: 'dataTool'
      });
    }

    if (this.props.operations.includes('duplicate')) {
      entry.operations.push({
        content: (
          <Button onClick={this.triggerDuplicate} noStyle>
            <Icon
              type="copy-document"
              title={`Duplicate ${this.props.api}`}
              className="duplicateIcon"
            />
          </Button>
        ),
        parentClassName: 'dataTool'
      });
    }

    if (this.props.operations.includes('delete')) {
      entry.operations.push({
        content: (
          <Button onClick={this.triggerDeleteModal} noStyle>
            <Icon type="delete" title={`Delete ${this.props.api}`} className="deleteIcon" />
          </Button>
        ),
        parentClassName: 'dataTool'
      });
    }

    return entry;
  };

  triggerDuplicate = evt => {
    evt.target.blur();
    this.props.duplicateEntity(this.props.data.id);
  };

  triggerDeleteModal = evt => {
    const {id, name} = this.props.data;
    return this.props.showDeleteModal({id, name});
  };

  renderCells = data => {
    return data.map((cell, idx) => (
      <span key={idx} className={classnames('data', cell.parentClassName)}>
        {this.renderCell(cell)}
      </span>
    ));
  };

  renderCell = cell => {
    if (cell.link) {
      return this.renderLink(cell);
    }
    return cell.content;
  };

  // if a ContentPanel is provided add onClick event otherwise use a router Link
  renderLink = data => {
    const {ContentPanel} = this.props;
    const allEntityData = data.editData;
    const EntityLink = ContentPanel ? 'a' : Link;
    const linkProps = {
      to: ContentPanel ? undefined : data.link,
      onClick: ContentPanel ? () => this.props.updateEntity(allEntityData) : undefined,
      className: data.className
    };
    return (
      <EntityLink {...linkProps}>
        {data.title ? (
          <React.Fragment>
            <span className="data visualizationIcon">{data.icon}</span>
            <div className="textInfo">
              <div className="data dataTitle">
                <h3>{formatters.getHighlightedText(data.title, this.props.query)}</h3>
                {allEntityData.reportType && allEntityData.reportType === 'combined' ? (
                  <span>Combined</span>
                ) : (
                  ''
                )}
              </div>
              <div className="extraInfo">{data.content}</div>
            </div>
          </React.Fragment>
        ) : (
          data.content
        )}
      </EntityLink>
    );
  };

  render() {
    const row = this.formatData(this.props.data);

    return (
      <li className="item">
        {this.renderLink({
          title: row.name,
          icon: row.icon,
          content: this.renderCells(row.infos),
          link: row.link,
          className: 'info',
          editData: row.editData
        })}
        <div className="operations">{this.renderCells(row.operations)}</div>
      </li>
    );
  }
}

EntityItem.defaultProps = {
  operations: ['create', 'edit', 'delete']
};
