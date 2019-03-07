import React from 'react';
import {Button, Icon} from 'components';

import {Link} from 'react-router-dom';

import entityIcons from './entityIcons';
import {getReportInfo, getReportIcon} from './service';
import LastModified from './subComponents/LastModified';
import NoEntities from './subComponents/NoEntities';
import classnames from 'classnames';

const HeaderIcon = entityIcons.report.header.Component;
const OpenCloseIcon = entityIcons.entityOpenClose;

class Reports extends React.Component {
  state = {
    open: true,
    limit: true
  };

  render() {
    const empty = this.props.reports.length === 0 && (
      <NoEntities label="Report" createFunction={this.props.createProcessReport} />
    );

    const ToggleButton = ({children}) =>
      this.props.reports.length > 0 ? (
        <Button className="ToggleCollapse" onClick={() => this.setState({open: !this.state.open})}>
          <OpenCloseIcon className={classnames('collapseIcon', {right: !this.state.open})} />
          {children}
        </Button>
      ) : (
        children
      );

    return (
      <div className="Reports">
        <div className="header">
          <ToggleButton>
            <h1>
              <HeaderIcon /> Reports
            </h1>
          </ToggleButton>
        </div>
        {this.state.open && (
          <>
            <ul className="entityList">
              {empty}
              {this.props.reports
                .slice(0, this.state.limit ? 5 : undefined)
                .map((itemData, idx) => {
                  const {Icon: ReportIcon, label} = getReportIcon(itemData);

                  return (
                    <li className="item" key={idx}>
                      <Link className="info" to={`/report/${itemData.id}`}>
                        <span className="icon" title={label}>
                          <ReportIcon />
                        </span>
                        <div className="textInfo">
                          <div className="data dataTitle">
                            <h3>{itemData.name}</h3>
                            {itemData.combined && <span>Combined</span>}
                            {itemData.reportType && itemData.reportType === 'decision' && (
                              <span>Decision</span>
                            )}
                          </div>
                          <div className="extraInfo">
                            <span className="data custom">{getReportInfo(itemData)}</span>
                            <LastModified
                              label="Last modified"
                              date={itemData.lastModified}
                              author={itemData.lastModifier}
                            />
                          </div>
                        </div>
                      </Link>
                      {this.props.renderCollectionsDropdown(itemData)}
                      <div className="operations">
                        <Link title="Edit Report" to={`/report/${itemData.id}/edit`}>
                          <Icon title="Edit Report" type="edit" className="editLink" />
                        </Link>
                        <Button
                          title="Duplicate Report"
                          onClick={this.props.duplicateReport(itemData)}
                        >
                          <Icon
                            type="copy-document"
                            title="Duplicate Report"
                            className="duplicateIcon"
                          />
                        </Button>
                        <Button
                          title="Delete Report"
                          onClick={this.props.showDeleteModalFor({
                            type: 'report',
                            entity: itemData
                          })}
                        >
                          <Icon type="delete" title="Delete Report" className="deleteIcon" />
                        </Button>
                      </div>
                    </li>
                  );
                })}
            </ul>
            {this.props.reports.length > 5 &&
              (this.state.limit ? (
                <>
                  {this.props.reports.length} Reports.{' '}
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

export default Reports;
