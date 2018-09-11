import React from 'react';
import moment from 'moment';
import classnames from 'classnames';

import {Icon} from 'components';
import {Link} from 'react-router-dom';
import entityIcons from './entityIcons';
import {formatters} from 'services';

import './EntityItem.css';

export default class EntityItem extends React.Component {
  getEntityIconName = entity => {
    if (entity.data && entity.data.visualization) return entity.data.visualization;
    return 'generic';
  };

  formatData = entity => {
    const {name, id, lastModified, lastModifier, shared} = entity;
    const entry = {
      name,
      link: `/${this.props.api}/${id}`,
      iconName: this.getEntityIconName(entity),
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
          <Icon
            type="copy-document"
            title={`Duplicate ${this.props.api}`}
            onClick={this.props.duplicateEntity(id)}
            className="duplicateIcon"
          />
        ),
        parentClassName: 'dataTool'
      });
    }

    if (this.props.operations.includes('delete')) {
      entry.operations.push({
        content: (
          <Icon
            type="delete"
            title={`Delete ${this.props.api}`}
            onClick={this.props.showDeleteModal({id, name})}
            className="deleteIcon"
          />
        ),
        parentClassName: 'dataTool'
      });
    }

    return entry;
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

  // if a modal is provided add onClick event otherwise use a router Link
  renderLink = data => {
    const {ContentPanel} = this.props;
    const EntityLink = ContentPanel ? 'a' : Link;
    const linkProps = {
      to: ContentPanel ? undefined : data.link,
      onClick: ContentPanel ? () => this.props.updateEntity(data.editData) : undefined,
      className: data.className
    };
    return (
      <EntityLink {...linkProps}>
        {data.title ? (
          <React.Fragment>
            <span className="data visualizationIcon">{data.icon}</span>
            <div className="textInfo">
              <span className="data dataTitle">
                {formatters.getHighlightedText(data.title, this.props.query)}
              </span>
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
    const EntityIcon = entityIcons[this.props.api][row.iconName];
    const EntityIconComponent = EntityIcon.label ? (
      <span title={EntityIcon.label}>
        <EntityIcon.Component />
      </span>
    ) : (
      <EntityIcon />
    );

    return (
      <li className="item">
        {this.renderLink({
          title: row.name,
          icon: EntityIconComponent,
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
