import React from 'react';
import ReactDOM from 'react-dom';

import AddButton from './AddButton'
import ReportTile from './ReportTile'
import GridTile from './GridTile'
import ReportSelectionModal from './ReportSelectionModal'

import './EditGrid.css'

export default class EditGrid extends React.Component {
  constructor(props) {
    super(props);
    this.state = this.getInitialState(props);
  }

  getInitialState(props) {
    const gridNums = 16;
    const gridMargin = 10;
    const availWidth = window.innerWidth - gridMargin * 3;
    const gridSize = availWidth / gridNums;

    let initialState = {
      gridNums : gridNums,
      gridMargin : gridMargin,
      availWidth : availWidth,
      gridSize : gridSize,
      modalVisible : false,
      buttonTop: 0,
      buttonLeft: 0,
      buttonSize: 2,
      dragState: {
        dragging: false,
        draggedReport: null
      }
    };

    initialState.tilesRows = this.initGridState(initialState, props);

    this.initButtonState(initialState);

    return initialState;
  }

  enoughSpaceForButton(cell, initialState) {
    for (let w = 0; w < initialState.buttonSize; w++ ) {
      for (let h = 0; h < initialState.buttonSize; h++ ) {
        if (initialState.tilesRows[cell.row + w][cell.col + h].hasReport) {
          return false;
        }
      }
    }

    return true;
  }

  initButtonState(initialState) {

    for (let row in initialState.tilesRows) {
      for (let col in initialState.tilesRows) {
        let cell = initialState.tilesRows[row][col];
        if (this.enoughSpaceForButton(cell, initialState)) {
          initialState.buttonTop = cell.row;
          initialState.buttonLeft = cell.col;
          return;
        }
      }
    }
  }

  initGridState(state, props) {
    const availHeight = this.calculateMaxRows(state, props);
    const tilesColumns = [];

    for (let row = 0; row < availHeight; row++) {
      tilesColumns[row] = {};
      for (let col = 0; col < state.gridNums; col++) {
        tilesColumns[row][col] = {
          row: row,
          col: col,
          highlighted: false,
          hasReport: false,
          inConflict: false
        };
      }
    }

    const reports = props.reports;

    if (reports) {
      for (let i = 0; i < reports.length; i++) {
        let report = reports[i];
        if (report.position) {
          for (let w = 0; w < report.dimensions.width; w++ ) {
            for (let h = 0; h < report.dimensions.height; h++ ) {
              tilesColumns[report.position.x + w][report.position.y + h].hasReport = true;
              tilesColumns[report.position.x + w][report.position.y + h].report = report;
            }
          }
        }
      }
    }
    return tilesColumns;
  }

  componentWillReceiveProps(newProps) {

    let newState = JSON.parse(JSON.stringify(this.state));

    newState.tilesRows = this.initGridState(newState, newProps);
    newState.modalVisible = false;
    newState.dragState =  {
      dragging: false,
      draggedReport: null
    };

    this.initButtonState(newState);

    this.setState(newState);
  }

  calculateMaxRows = (state, props) => {
    const {gridMargin, offsetTop = 0, gridSize} = state;

    //number of rows to render all reports + add button below
    const reportRows = this.maxReportRows(state, props);

    //number of rows fitting the screen without reports
    const rowsWithoutReports = (window.innerHeight - gridMargin * 4 - offsetTop)
      / (gridSize + gridMargin);

    const screenRows = Math.round(Math.abs(rowsWithoutReports));

    return Math.max(screenRows, reportRows);
  }

  maxReportRows(state, props) {
    let reportRows = 0;
    const reports = props.reports;
    if (reports) {
      let maxRow = 0;
      let maxH = 0;
      for (let i = 0; i < reports.length; i++) {
        if ((reports[i].position.x + reports[i].dimensions.height) > (maxRow + maxH)) {
          maxRow = reports[i].position.x;
          maxH = reports[i].dimensions.height;
        }
      }
      reportRows = maxRow + maxH + state.buttonSize;
    }
    return reportRows;
  }

  highlightIn = (inRow, inCol) => {
    const report = this.state.dragState.draggedReport;
    let newState = JSON.parse(JSON.stringify(this.state));

    let affectedCells = [];
    let inGrid = true;
    let inConflict = false;
    for (let w = 0; w < report.dimensions.width; w++ ) {
      for (let h = 0; h < report.dimensions.height; h++ ) {
        const column = inCol + h;
        const row = inRow + w;

        if (newState.tilesRows[row] && newState.tilesRows[row][column]) {
          const currentTile = newState.tilesRows[row][column];
          affectedCells.push(currentTile);
          currentTile.highlighted = true;

          if (currentTile.hasReport &&
            this.notSameReport(currentTile.report, newState.dragState.draggedReport)) {
            inConflict = true;
            newState.dragState.inConflict = true;
          }
        } else {
          inGrid = false;
        }
      }
    }

    if (!inGrid || inConflict) {
      for (let i = 0; i < affectedCells.length; i++) {
        affectedCells[i].inConflict = true;
      }
    }

    this.setState(newState);
  }

  notSameReport(r1, r2) {
    return this.reportKey(r1) !== this.reportKey(r2);
  }

  reportKey(reprot) {
    return reprot.id + '-' + reprot.position.x + '-' + reprot.position.y;
  }

  highlightOut = (inRow, inCol, resetReport) => {
    const report = this.state.dragState.draggedReport;
    let newState = JSON.parse(JSON.stringify(this.state));

    for (let w = 0; w < report.dimensions.width; w++ ) {
      for (let h = 0; h < report.dimensions.height; h++ ) {

        const column = inCol + h;
        const row = inRow + w;

        if (newState.tilesRows[row] && newState.tilesRows[row][column]) {

          const currentTile = newState.tilesRows[row][column];
          currentTile.highlighted = false;
          currentTile.inConflict = false;
          newState.dragState.inConflict = false;

          if (resetReport) {
            currentTile.report = null;
          }
        }
      }
    }

    this.setState(newState);
  }

  generateGridRows = () => {
    const result = [];

    const state = this.state;

    if (state && state.tilesRows) {
      for(let row in state.tilesRows) {
        for (let col in state.tilesRows[row]) {

          const currentTile = state.tilesRows[row][col];

          result.push(
            (<GridTile
              gridSize={state.gridSize}
              gridMargin={state.gridMargin}
              row={currentTile.row}
              col={currentTile.col}
              highlightIn={this.highlightIn.bind(this)}
              highlightOut={this.highlightOut.bind(this)}

              reportDroped={this.processDrop.bind(this)}
              highlighted={currentTile.highlighted}
              hasReport={currentTile.hasReport}
              inConflict={currentTile.inConflict}
              key={'row-' + row + '-col-' + col}
            />)
          );
        }
      }
    }


    return result;
  }

  processDragStart = (report) => {
    this.setState({
      dragState: {
        dragging: true,
        draggedReport: report
      }
    });
  }

  processDragEnd = () => {
    this.setState({
      dragState: {
        dragging: false,
        draggedReport: null
      }
    });
  }

  processDrop = (data) => {

    this.highlightOut(data.row, data.col, true);
    let newState = JSON.parse(JSON.stringify(this.state));
    let updatedReport = newState.dragState.inConflict ?
      newState.dragState.draggedReport : JSON.parse(JSON.stringify(newState.dragState.draggedReport));
    const oldReport = newState.dragState.draggedReport;

    updatedReport.position.x = data.row;
    updatedReport.position.y = data.col;

    this.props.onReportMoved(oldReport,updatedReport);
  }

  generateReportCards = () => {
    let reportTiles = [];

    const state = this.state;
    const reports = this.props.reports;

    if (reports) {
      for (let i = 0; i < reports.length; i++) {
        const report = reports[i];
        const dragged = state.dragState && state.dragState.dragging && !this.notSameReport(report, state.dragState.draggedReport);
        reportTiles.push(
          <ReportTile
            key={this.reportKey(report)}
            gridMargin={state.gridMargin}
            gridSize={state.gridSize}
            data={report}
            dragged={dragged}
            onDragEnd={this.processDragEnd.bind(this)}
            startDrag={this.processDragStart.bind(this)}
          />
        );
      }
    }

    return <div className='reports'>
      {reportTiles}
    </div>
  }

  addReport = (data) => {
    const dimensions = {
      width: this.state.buttonSize,
      height: this.state.buttonSize
    };

    const position = {
      x: this.state.buttonTop,
      y: this.state.buttonLeft
    };

    this.props.onReportSelected(data, position , dimensions);
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
    var rect = ReactDOM.findDOMNode(this)
      .getBoundingClientRect();
    this.setState({offsetTop : rect.top});
  }


  render() {

    return (
      <div className='edit-grid'>
        <div className='edit-grid--grid'>
          {this.generateGridRows()}
          {this.generateReportCards()}
          {

            (this.state && this.state.dragState && !this.state.dragState.dragging) ?
              <AddButton
                buttonSize={this.state.buttonSize}
                buttonTop={this.state.buttonTop}
                buttonLeft={this.state.buttonLeft}
                gridMargin={this.state.gridMargin}
                gridSize={this.state.gridSize}
                onClick={this.showModal}
              /> : ''
          }

        </div>
        {
          (this.state && this.state.modalVisible) ?
            <ReportSelectionModal onSelectReport={this.addReport}
                                  onCloseModal={this.closeModal}
            /> : ''
        }
      </div>
    )

  }

}
