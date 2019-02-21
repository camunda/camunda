import React from 'react';
import {Button, Icon, ConfirmationModal, Message, LoadingIndicator} from 'components';

import {withErrorHandling} from 'HOC';
import {Redirect, Link} from 'react-router-dom';

import entityIcons from './entityIcons';
import {createDashboard, loadDashboards, deleteDashboard} from './service';
import LastModified from './subComponents/LastModified';
import NoEntities from './subComponents/NoEntities';

const HeaderIcon = entityIcons.dashboard.header.Component;
const EntityIcon = entityIcons.dashboard.generic.Component;

class Dashboards extends React.Component {
  state = {
    redirect: false,
    loading: true,
    entities: [],
    deleting: false,
    open: true,
    limit: true
  };

  loadDashboards = async () => {
    this.props.mightFail(loadDashboards(), response => {
      this.setState({
        entities: response,
        loading: false
      });
    });
  };

  componentDidMount = this.loadDashboards;

  createDashboard = async () => this.setState({redirect: await createDashboard()});

  duplicateDashboard = dashboard => async evt => {
    evt.target.blur();

    const copy = {
      ...dashboard,
      name: dashboard.name + ' - Copy'
    };
    await createDashboard(copy);
    this.loadDashboards();
  };

  showDeleteModalFor = dashboard => () => this.setState({deleting: dashboard});
  hideDeleteModal = () => this.setState({deleting: false});

  deleteDashboard = () => {
    deleteDashboard(this.state.deleting.id);
    this.setState(({entities, deleting}) => {
      return {
        entities: entities.filter(entity => entity !== deleting),
        deleting: false
      };
    });
  };

  render() {
    if (this.state.redirect) {
      return <Redirect to={`/dashboard/${this.state.redirect}/edit?new`} />;
    }

    const error = this.props.error && (
      <Message type="error">{this.props.error.errorMessage || this.props.error.statusText}</Message>
    );

    const loading = this.state.loading && <LoadingIndicator />;

    const empty = !loading && this.state.entities.length === 0 && (
      <NoEntities label="Dashboard" createFunction={this.createDashboard} />
    );

    const ToggleButton = ({children}) =>
      this.state.entities.length > 0 ? (
        <Button className="ToggleCollapse" onClick={() => this.setState({open: !this.state.open})}>
          <Icon className="collapseIcon" size="30px" type={this.state.open ? 'down' : 'right'} />
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
          <Button color="green" className="createButton" onClick={this.createDashboard}>
            Create New Dashboard
          </Button>
        </div>
        {this.state.open && (
          <>
            {error}
            {loading}
            <ul className="entityList">
              {empty}
              {this.state.entities
                .slice(0, this.state.limit ? 5 : undefined)
                .map((itemData, idx) => (
                  <li key={idx}>
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
                            date={itemData.lastModified}
                            author={itemData.lastModifier}
                          />
                        </div>
                      </div>
                    </Link>
                    <div className="operations">
                      <Link title="Edit Dashboard" to={`/dashboard/${itemData.id}/edit`}>
                        <Icon title="Edit Dashboard" type="edit" className="editLink" />
                      </Link>
                      <Button
                        title="Duplicate Dashboard"
                        onClick={this.duplicateDashboard(itemData)}
                      >
                        <Icon
                          type="copy-document"
                          title="Duplicate Dashboard"
                          className="duplicateIcon"
                        />
                      </Button>
                      <Button title="Delete Dashboard" onClick={this.showDeleteModalFor(itemData)}>
                        <Icon type="delete" title="Delete Dashboard" className="deleteIcon" />
                      </Button>
                    </div>
                  </li>
                ))}
            </ul>
            {!empty &&
              !loading &&
              this.state.entities.length > 5 &&
              (this.state.limit ? (
                <>
                  {this.state.entities.length} Dashboards.{' '}
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
        <ConfirmationModal
          open={this.state.deleting !== false}
          onClose={this.hideDeleteModal}
          onConfirm={this.deleteDashboard}
          entityName={this.state.deleting && this.state.deleting.name}
        />
      </div>
    );
  }
}

export default withErrorHandling(Dashboards);
