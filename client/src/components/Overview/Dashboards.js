import React from 'react';
import {Button, Icon, ConfirmationModal, Message, LoadingIndicator, Input} from 'components';

import {withErrorHandling} from 'HOC';
import {Redirect, Link} from 'react-router-dom';
import {formatters} from 'services';

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
    search: ''
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

  updateSearch = ({target: {value}}) => this.setState({search: value});

  render() {
    if (this.state.redirect) {
      return <Redirect to={`/dashboard/${this.state.redirect}/edit?new`} />;
    }

    const error = this.props.error && (
      <Message type="error">{this.props.error.errorMessage || this.props.error.statusText}</Message>
    );

    const loading = this.state.loading && <LoadingIndicator />;

    const empty = !loading &&
      this.state.entities.length === 0 && (
        <NoEntities label="Dashboard" createFunction={this.createDashboard} />
      );

    const search = !loading &&
      !empty && (
        <Input className="searchInput" placeholder="Filter for name" onChange={this.updateSearch} />
      );

    return (
      <div className="Dashboards">
        <h1>
          <HeaderIcon /> Dashboards
        </h1>
        <Button color="green" className="createButton" onClick={this.createDashboard}>
          Create New Dashboard
        </Button>
        {error}
        {search}
        {loading}
        <ul>
          {empty}
          {this.state.entities
            .filter(({name}) => name.toLowerCase().includes(this.state.search.toLowerCase()))
            .map((itemData, idx) => (
              <li key={idx}>
                <Link className="info" to={`/dashboard/${itemData.id}`}>
                  <span className="icon">
                    <EntityIcon />
                  </span>
                  <div className="textInfo">
                    <div className="data dataTitle">
                      <h3>{formatters.getHighlightedText(itemData.name, this.state.search)}</h3>
                    </div>
                    <div className="extraInfo">
                      <span className="data custom">
                        {itemData.reports.length} Report{itemData.reports.length !== 1 ? 's' : ''}
                      </span>
                      <LastModified date={itemData.lastModified} author={itemData.lastModifier} />
                    </div>
                  </div>
                </Link>
                <div className="operations">
                  <Link to={`/dashboard/${itemData.id}/edit`}>
                    <Icon type="edit" className="editLink" />
                  </Link>
                  <Button onClick={this.duplicateDashboard(itemData)}>
                    <Icon
                      type="copy-document"
                      title="Duplicate Dashboard"
                      className="duplicateIcon"
                    />
                  </Button>
                  <Button onClick={this.showDeleteModalFor(itemData)}>
                    <Icon type="delete" title="Delete Dashboard" className="deleteIcon" />
                  </Button>
                </div>
              </li>
            ))}
        </ul>
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
