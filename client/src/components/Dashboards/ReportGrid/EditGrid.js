import React from 'react';
import ReactDOM from 'react-dom';

import AddButton from './AddButton'
import ReportTile from './ReportTile'
import ReportSelectionModal from './ReportSelectionModal'

import './EditGrid.css'

export default class EditGrid extends React.Component {
  constructor(props) {
    super(props);

    const gridNums = 16;
    const gridMargin = 10;
    const availWidth = window.innerWidth - gridMargin * 3;
    const gridSize = availWidth / gridNums;

    this.state = {
      gridNums : gridNums,
      gridMargin : gridMargin,
      availWidth : availWidth,
      gridSize : gridSize,
      modalVisible : false,
      buttonTop: 0,
      buttonLeft: 0,
      buttonSize: 2,
      reports: this.props.reports
    };

    let maxX = 0;
    let lastReport = this.props.reports && this.props.reports.length > 0 ? this.props.reports[0] : null;

    if (lastReport) {
      for (let i = 0; i < this.props.reports.length; i++) {
        if (this.props.reports[i].position.x > maxX) {
          maxX = this.props.reports[i].position.x;
          lastReport = this.props.reports[i];
        }
      }

      for (let j = 0; j < this.props.reports.length; j++) {
        if (
          this.props.reports[j].position.x === lastReport.position.x &&
          this.props.reports[j].position.y > lastReport.position.y
        ) {
          lastReport = this.props.reports[j];
        }
      }

      this.state.buttonTop = lastReport.position.x;
      this.state.buttonLeft = lastReport.position.y;
    }
  }

  calculateMaxRows = (gridMargin, gridSize, offsetTop = 0) => {
    //TODO:number of rows needed to render all reports

    //number of rows fitting the screen without reports
    const rowsWithoutReports = (window.innerHeight - gridMargin * 4 - offsetTop)
      / (gridSize + gridMargin);

    //TODO:chose max
    //add rows for plus button
    return Math.abs(rowsWithoutReports);
  }

  generateGridRows = (state) => {

    const availHeight = this.calculateMaxRows(state.gridMargin, state.gridSize, state.offsetTop);
    const result = [];


    for(let i = 0; i < state.gridNums; i++) {
      for (let j = 0; j < availHeight; j++) {
        let cellStyle = {
          top: (state.gridSize * j) + state.gridMargin + 'px',
          left: (state.gridSize * i) + state.gridMargin + 'px',
          width: state.gridSize - state.gridMargin + 'px',
          height: state.gridSize - state.gridMargin + 'px'
        };

        result.push(<div className={'grid-tile'} key={'row-' + i + '-cell-' +j} style={cellStyle}></div>);
      }
    }

    return result;
  }

  generateReportCards = (state) => {
    let reportTiles = [];

    if (state.reports) {
      for (let i = 0; i < state.reports.length; i++) {
        let report = state.reports[i];
        reportTiles.push(
          <ReportTile
            key={report.id + '_' + i}
            gridMargin={this.state.gridMargin}
            gridSize={this.state.gridSize}
            data={report}
          />
        );
      }
    }

    return <div className={'reports'}>
      {reportTiles}
    </div>
  }

  addReport = (data) => {

    this.props.onReportSelected(data, {
      x: this.state.buttonTop,
      y: this.state.buttonLeft
    }, {
      width: this.state.buttonSize,
      height: this.state.buttonSize
    });

    this.moveButton();
  }

  moveButton = () => {
    let buttonFitsInARow = this.state.gridNums - (this.state.buttonLeft + this.state.buttonSize * 2) >= 0;

    let newButtonPosition = buttonFitsInARow ? {
      buttonTop : this.state.buttonTop,
      buttonLeft : this.state.buttonLeft + this.state.buttonSize
    } : {
      buttonTop : this.state.buttonTop + this.state.buttonSize,
      buttonLeft : 0
    };

    this.setState(newButtonPosition);
  }

  closeModal = () => {
    this.setState({
      modalVisible: false
    });
  }

  showModal = () => {
    this.setState({
      modalVisible: true
    });
  }

  componentDidMount = () => {
    if (this.props.reports && this.props.reports.length > 0) {
      this.moveButton();
    }
    var rect = ReactDOM.findDOMNode(this)
      .getBoundingClientRect();
    this.setState({offsetTop : rect.top});
  }


  render() {
    return (
      <div className={'edit-grid'}>
        <div className={'grid'}>
          {this.generateGridRows(this.state)}
          {this.generateReportCards(this.state)}
          <AddButton
            buttonSize={this.state.buttonSize}
            buttonTop={this.state.buttonTop}
            buttonLeft={this.state.buttonLeft}
            gridMargin={this.state.gridMargin}
            gridSize={this.state.gridSize}
            onClick={this.showModal.bind(this)}
          />
        </div>
        {
          this.state.modalVisible ?
            <ReportSelectionModal onSelectReport={this.addReport.bind(this)}
                                  onCloseModal={this.closeModal.bind(this)}
            /> : ''
        }
      </div>
    )

  }
}
