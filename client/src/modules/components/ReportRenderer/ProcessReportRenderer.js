import React from 'react';

import {LoadingIndicator} from 'components';
import {getFlowNodeNames} from 'services';
import ReportBlankSlate from './ReportBlankSlate';

import {isEmpty, getFormatter} from './service';

import {Number, Table, Heatmap, Chart} from './visualizations';

export default class ProcessReportRenderer extends React.Component {
  state = {
    flowNodeNames: null,
    loaded: false
  };

  componentDidMount() {
    const {processDefinitionVersion, processDefinitionKey} = this.props.report.data;
    if (processDefinitionKey && processDefinitionVersion) {
      this.loadFlowNodeNames(processDefinitionKey, processDefinitionVersion);
    }
  }

  async componentDidUpdate(prevProps) {
    const {
      processDefinitionVersion: nextProcDefVersion,
      processDefinitionKey: nextProcDefKey
    } = this.props.report.data;

    const {
      processDefinitionVersion: prevProcDefVersion,
      processDefinitionKey: prevProcDefKey
    } = prevProps.report.data;

    const procDefKeyChanged = nextProcDefKey && nextProcDefKey !== prevProcDefKey;
    const procDefVersionChanged = nextProcDefVersion && nextProcDefVersion !== prevProcDefVersion;
    if (procDefKeyChanged || procDefVersionChanged) {
      await this.loadFlowNodeNames(nextProcDefKey, nextProcDefVersion);
    }
  }

  loadFlowNodeNames = async (key, version) => {
    this.setState({loaded: false});
    this.setState({
      flowNodeNames: await getFlowNodeNames(key, version),
      loaded: true
    });
  };

  render() {
    const somethingMissing = this.checkReport();
    if (somethingMissing) {
      return (
        <ReportBlankSlate
          errorMessage={'To display a report, please select ' + somethingMissing + '.'}
        />
      );
    }

    const {report} = this.props;

    if (!this.state.loaded) {
      return <LoadingIndicator />;
    }

    const Component = this.getComponent();

    const props = {
      ...this.props,
      formatter: getFormatter(report.data.view.property),
      flowNodeNames: this.state.flowNodeNames,
      report: {...this.props.report, result: processResult(this.props.report)}
    };

    return (
      <div className="component">
        <Component {...props} />
      </div>
    );
  }

  checkReport = () => {
    const {
      report: {data}
    } = this.props;

    if (isEmpty(data.processDefinitionKey) || isEmpty(data.processDefinitionVersion)) {
      return 'a Process Definition';
    } else if (!data.view) {
      return 'an option for ”View”';
    } else if (!data.groupBy) {
      return 'an option for ”Group by”';
    } else if (!data.visualization) {
      return 'an option for ”Visualize as”';
    } else {
      return;
    }
  };

  getComponent = () => {
    switch (this.props.report.data.visualization) {
      case 'number':
        return Number;
      case 'table':
        return Table;
      case 'bar':
      case 'line':
      case 'pie':
        return Chart;
      case 'heat':
        return Heatmap;
      default:
        return ReportBlankSlate;
    }
  };
}

function processResult(report) {
  if (report.data.view.property.toLowerCase().includes('duration')) {
    if (report.resultType === 'durationNumber') {
      return report.result.avg;
    }
    if (report.resultType === 'durationMap') {
      return Object.entries(report.result).reduce((result, [key, {avg}]) => {
        result[key] = avg;
        return result;
      }, {});
    }
  }
  return report.result;
}
