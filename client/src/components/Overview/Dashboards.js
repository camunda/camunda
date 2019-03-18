import React from 'react';
import {Button} from 'components';

import entityIcons from './entityIcons';
import DashboardItem from './subComponents/DashboardItem';
import NoEntities from './subComponents/NoEntities';
import classnames from 'classnames';
import {withStore} from './OverviewStore';

const HeaderIcon = entityIcons.dashboard.header.Component;
const OpenCloseIcon = entityIcons.entityOpenClose;

class Dashboards extends React.Component {
  state = {
    open: true,
    limit: true
  };

  componentDidMount = this.loadDashboards;

  render() {
    const {dashboards} = this.props.store;
    const empty = dashboards.length === 0 && (
      <NoEntities label="Dashboard" createFunction={this.props.createDashboard} />
    );

    const ToggleButton = ({children}) =>
      !empty ? (
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
            <ul className="entityList">
              {empty}
              {dashboards.slice(0, this.state.limit ? 5 : undefined).map(dashboard => (
                <DashboardItem
                  key={dashboard.id}
                  dashboard={dashboard}
                  duplicateEntity={this.props.duplicateEntity}
                  showDeleteModalFor={this.props.showDeleteModalFor}
                />
              ))}
            </ul>
            {dashboards.length > 5 &&
              (this.state.limit ? (
                <>
                  {dashboards.length} Dashboards.{' '}
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

export default withStore(Dashboards);
