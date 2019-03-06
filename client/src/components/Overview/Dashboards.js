import React from 'react';
import {Button, Icon, Message} from 'components';

import {Link} from 'react-router-dom';

import entityIcons from './entityIcons';
import LastModified from './subComponents/LastModified';
import NoEntities from './subComponents/NoEntities';
import classnames from 'classnames';

const HeaderIcon = entityIcons.dashboard.header.Component;
const EntityIcon = entityIcons.dashboard.generic.Component;
const OpenCloseIcon = entityIcons.entityOpenClose;

class Dashboards extends React.Component {
  state = {
    open: true,
    limit: true
  };

  componentDidMount = this.loadDashboards;

  render() {
    const error = this.props.error && (
      <Message type="error">{this.props.error.errorMessage || this.props.error.statusText}</Message>
    );

    const empty = this.props.dashboards.length === 0 && (
      <NoEntities label="Dashboard" createFunction={this.props.createDashboard} />
    );

    const ToggleButton = ({children}) =>
      this.props.dashboards.length > 0 ? (
        <Button className="ToggleCollapse" onClick={() => this.setState({open: !this.state.open})}>
          <OpenCloseIcon className={classnames('collapseIcon', {right: !this.state.open})} />
          {children}
        </Button>
      ) : (
        children
      );

    return (
      <div className="Dashboards">
        <div className="header">
          <ToggleButton>
            <h1>
              <HeaderIcon /> Dashboards
            </h1>
          </ToggleButton>
        </div>
        {this.state.open && (
          <>
            {error}
            <ul className="entityList">
              {empty}
              {this.props.dashboards
                .slice(0, this.state.limit ? 5 : undefined)
                .map((itemData, idx) => (
                  <li className="item" key={idx}>
                    <Link className="info" to={`/dashboard/${itemData.id}`}>
                      <span className="icon">
                        <EntityIcon />
                      </span>
                      <div className="textInfo">
                        <div className="data dataTitle">
                          <h3>{itemData.name}</h3>
                        </div>
                        <div className="extraInfo">
                          <span className="data custom">
                            {itemData.reports.length} Report
                            {itemData.reports.length !== 1 ? 's' : ''}
                          </span>
                          <LastModified
                            label="Last modified"
                            date={itemData.lastModified}
                            author={itemData.lastModifier}
                          />
                        </div>
                      </div>
                    </Link>
                    <div className="entityCollections" />
                    <div className="operations">
                      <Link title="Edit Dashboard" to={`/dashboard/${itemData.id}/edit`}>
                        <Icon title="Edit Dashboard" type="edit" className="editLink" />
                      </Link>
                      <Button
                        title="Duplicate Dashboard"
                        onClick={this.props.duplicateDashboard(itemData)}
                      >
                        <Icon
                          type="copy-document"
                          title="Duplicate Dashboard"
                          className="duplicateIcon"
                        />
                      </Button>
                      <Button
                        title="Delete Dashboard"
                        onClick={this.props.showDeleteModalFor({
                          type: 'dashboards',
                          entity: itemData
                        })}
                      >
                        <Icon type="delete" title="Delete Dashboard" className="deleteIcon" />
                      </Button>
                    </div>
                  </li>
                ))}
            </ul>
            {this.props.dashboards.length > 5 &&
              (this.state.limit ? (
                <>
                  {this.props.dashboards.length} Dashboards.{' '}
                  <Button type="link" onClick={() => this.setState({limit: false})}>
                    Show all...
                  </Button>
                </>
              ) : (
                <Button type="link" onClick={() => this.setState({limit: true})}>
                  Show less...
                </Button>
              ))}
          </>
        )}
      </div>
    );
  }
}

export default Dashboards;
