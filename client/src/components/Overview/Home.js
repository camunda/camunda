import React from 'react';
import {Icon, Message, LoadingIndicator} from 'components';

import {withErrorHandling} from 'HOC';
import {Link} from 'react-router-dom';

import './Home.scss';
import entityIcons from './entityIcons';
import {
  createDashboard,
  loadDashboards,
  loadReports,
  createReport,
  getReportInfo,
  getReportIcon
} from './service';
import LastModified from './subComponents/LastModified';
import NoEntities from './subComponents/NoEntities';

const DashboardHeaderIcon = entityIcons.dashboard.header.Component;
const DashboardEntityIcon = entityIcons.dashboard.generic.Component;

const ReportHeaderIcon = entityIcons.report.header.Component;

class Home extends React.Component {
  state = {
    reports: [],
    loadingReports: true,
    dashboards: [],
    loadingDashboards: true
  };

  componentDidMount = async () => {
    this.props.mightFail(loadDashboards(5), response => {
      this.setState({
        dashboards: response,
        loadingDashboards: false
      });
    });
    this.props.mightFail(loadReports(), response => {
      this.setState({
        reports: response,
        loadingReports: false
      });
    });
  };

  createDashboard = async () => this.setState({redirect: await createDashboard()});
  createReport = async () => this.setState({redirect: await createReport(false)});

  render() {
    if (this.props.error) {
      return (
        <Message type="error">
          {this.props.error.errorMessage || this.props.error.statusText}
        </Message>
      );
    }

    return (
      <div className="Home">
        {this.renderDashboards()}
        {this.renderReports()}
      </div>
    );
  }

  renderDashboards() {
    const loading = this.state.loadingDashboards && <LoadingIndicator />;

    const empty = !loading &&
      this.state.dashboards.length === 0 && (
        <NoEntities label="Dashboard" createFunction={this.createDashboard} />
      );

    return (
      <div className="dashboards">
        <h1>
          <DashboardHeaderIcon /> Dashboards
        </h1>
        {loading}
        <ul>
          {empty}
          {this.state.dashboards.map((itemData, idx) => (
            <li key={idx}>
              <Link className="info" to={`/dashboard/${itemData.id}`}>
                <span className="icon">
                  <DashboardEntityIcon />
                </span>
                <div className="textInfo">
                  <div className="data dataTitle">
                    <h3>{itemData.name}</h3>
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
              </div>
            </li>
          ))}
        </ul>
        {!empty &&
          !loading && (
            <Link to="/dashboards" className="small">
              View all Dashboards…
            </Link>
          )}
      </div>
    );
  }

  renderReports() {
    const loading = this.state.loadingReports && <LoadingIndicator />;

    const empty = !loading &&
      this.state.reports.length === 0 && (
        <NoEntities label="Report" createFunction={this.createReport} />
      );

    return (
      <div className="reports">
        <h1>
          <ReportHeaderIcon /> Reports
        </h1>
        {loading}
        <ul>
          {empty}
          {this.state.reports.slice(0, 5).map((itemData, idx) => {
            const {Icon: ReportIcon, label} = getReportIcon(itemData, this.state.reports);

            return (
              <li key={idx}>
                <Link className="info" to={`/report/${itemData.id}`}>
                  <span className="icon" title={label}>
                    <ReportIcon />
                  </span>
                  <div className="textInfo">
                    <div className="data dataTitle">
                      <h3>{itemData.name}</h3>
                      {itemData.combined && <span>Combined</span>}
                    </div>
                    <div className="extraInfo">
                      <span className="data custom">{getReportInfo(itemData)}</span>
                      <LastModified date={itemData.lastModified} author={itemData.lastModifier} />
                    </div>
                  </div>
                </Link>
                <div className="operations">
                  <Link to={`/report/${itemData.id}/edit`}>
                    <Icon type="edit" className="editLink" />
                  </Link>
                </div>
              </li>
            );
          })}
        </ul>
        {!empty &&
          !loading && (
            <Link to="/reports" className="small">
              View all Reports…
            </Link>
          )}
      </div>
    );
  }
}

export default withErrorHandling(Home);
