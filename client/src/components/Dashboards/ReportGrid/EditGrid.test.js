import React from 'react';
import {mount} from 'enzyme';

import EditGrid from './EditGrid';
import {load} from '../../EntityList/service';

jest.mock('../../EntityList/service', () => {
  return {
    load: jest.fn()
  }
});

let sampleReports;

beforeEach(() => {
  sampleReports = [
    {
      id: 1,
      name : 'r1',
      position: {x: 0, y: 0},
      dimensions: {width: 2, height: 2}
    },
    {
      id: 2,
      name: 'r2',
      position: {x: 0, y: 2},
      dimensions: {width: 2, height: 2}
    }
  ];
  load.mockReturnValue(sampleReports);
});

const selectionMock = jest.fn();
const removeMock = jest.fn();
const moveMock = jest.fn();

it('should render add button', async () => {
  const node = mount(<EditGrid onReportSelected={selectionMock} onReportRemoved={removeMock} reports={[]}/>);

  expect(node.find('.add-button--container')).toBePresent();
  expect(load).not.toHaveBeenCalled();
  expect(node).not.toIncludeText('Select a report from the list');
});

it('should render reports', async () => {
  const node = mount(<EditGrid onReportSelected={selectionMock} onReportRemoved={removeMock} reports={sampleReports}/>);

  expect(node).toIncludeText('r1');
  expect(node).toIncludeText('r2');

  expect(load).not.toHaveBeenCalled();
  expect(node).not.toIncludeText('Select a report from the list');
});

it('should invoke callback on selection', async () => {
  const node = mount(<EditGrid onReportSelected={selectionMock} onReportRemoved={removeMock} reports={[]}/>);

  node.instance().addReport({id: 'stub report'});
  expect(selectionMock).toBeCalledWith({id: 'stub report'}, {
    x: 0,
    y: 0
  }, {
    width: 2,
    height: 2
  });
});

it('should show modal on add click', async () => {
  const node = mount(<EditGrid onReportSelected={selectionMock} onReportRemoved={removeMock} reports={[]}/>);

  node.find('.add-button--container').simulate('click');
  expect(node.find('.report-selection-modal--modal')).toBePresent();
});

describe ('drag\'n\'drop', async () => {
  it ('should mark tiles with reports on load' , async () => {
    const node = mount(<EditGrid onReportSelected={selectionMock} onReportRemoved={removeMock} reports={sampleReports}/>);

    expect(node.instance().state.tilesRows[0][0].hasReport).toEqual(true);
    expect(node.instance().state.tilesRows[0][1].hasReport).toEqual(true);
    expect(node.instance().state.tilesRows[1][0].hasReport).toEqual(true);
    expect(node.instance().state.tilesRows[1][1].hasReport).toEqual(true);
  });

  describe ('on drag in', () => {
    it ('should mark conflict if adjacent', async () => {
      sampleReports[1].position.y = 4;

      const node = mount(<EditGrid onReportSelected={selectionMock} onReportRemoved={removeMock} reports={sampleReports}/>);
      expect(node.instance().state.tilesRows[0][3].hasReport).toEqual(false);
      expect(node.instance().state.tilesRows[0][4].hasReport).toEqual(true);
      expect(node.instance().state.tilesRows[0][3].highlighted).toEqual(false);
      expect(node.instance().state.dragState.inConflict).toEqual(undefined);

      node.instance().processDragStart(sampleReports[0]);
      node.instance().highlightIn(0,3);
      expect(node.instance().state.tilesRows[0][3].highlighted).toEqual(true);
      expect(node.instance().state.tilesRows[0][3].inConflict).toEqual(true);
    })

    it ('should not indicate conflict if same tile is dragged', async () => {
      sampleReports[1].position.y = 4;

      const node = mount(<EditGrid onReportSelected={selectionMock} onReportRemoved={removeMock} reports={sampleReports}/>);
      expect(node.instance().state.tilesRows[0][3].hasReport).toEqual(false);
      expect(node.instance().state.tilesRows[0][4].hasReport).toEqual(true);
      expect(node.instance().state.tilesRows[0][3].highlighted).toEqual(false);
      expect(node.instance().state.dragState.inConflict).toEqual(undefined);

      node.instance().processDragStart(sampleReports[1]);
      node.instance().highlightIn(0,3);
      expect(node.instance().state.tilesRows[0][3].highlighted).toEqual(true);
      expect(node.instance().state.tilesRows[0][3].inConflict).toEqual(false);
    })

    it ('should mark conflict on last row\\column', () => {
      const node = mount(<EditGrid onReportSelected={selectionMock} onReportRemoved={removeMock} reports={sampleReports}/>);

      node.instance().processDragStart(sampleReports[0]);
      node.instance().highlightIn(0,15);
      expect(node.instance().state.tilesRows[0][15].highlighted).toEqual(true);
      expect(node.instance().state.tilesRows[0][15].inConflict).toEqual(true);
    })
  });

  describe('on drag out', () => {
    it ('should remove highlight', () => {
      const node = mount(<EditGrid onReportSelected={selectionMock} onReportRemoved={removeMock} reports={sampleReports}/>);

      node.instance().processDragStart(sampleReports[0]);
      node.instance().highlightIn(0,15);
      node.instance().highlightOut(0,15);
      expect(node.instance().state.tilesRows[0][15].highlighted).toEqual(false);
    })

    it ('should remove conflict', () => {
      const node = mount(<EditGrid onReportSelected={selectionMock} onReportRemoved={removeMock} reports={sampleReports}/>);

      node.instance().processDragStart(sampleReports[0]);
      node.instance().highlightIn(0,15);
      node.instance().highlightOut(0,15);
      expect(node.instance().state.tilesRows[0][15].inConflict).toEqual(false);
    })
  })

  it('should reset on props update', () => {

    const node = mount(<EditGrid onReportSelected={selectionMock} onReportRemoved={removeMock} reports={sampleReports}/>);

    node.instance().processDragStart(sampleReports[0]);
    node.instance().highlightIn(0,15);
    node.instance().componentWillReceiveProps(sampleReports);

    expect(node.instance().state.tilesRows[0][15].inConflict).toEqual(false);
    expect(node.instance().state.tilesRows[0][15].highlighted).toEqual(false);
    expect(node.instance().state.modalVisible).toEqual(false);
    expect(node.instance().state.dragState.dragging).toEqual(false);
  })

  describe('on drop', () => {
    it('should reset highlight', () => {
      const node = mount(<EditGrid
        onReportSelected={selectionMock}
        onReportMoved={moveMock}
        onReportRemoved={removeMock}
        reports={sampleReports}/>
      );
      node.instance().processDragStart(sampleReports[0]);
      node.instance().highlightIn(0,13);
      expect(node.instance().state.tilesRows[0][13].highlighted).toEqual(true);
      node.instance().processDrop({row: 0, col: 13});
      expect(node.instance().state.tilesRows[0][13].highlighted).toEqual(false);
    })

    it('should propagate report update if no conflict', () => {
      const node = mount(<EditGrid
        onReportSelected={selectionMock}
        onReportMoved={moveMock}
        onReportRemoved={removeMock}
        reports={sampleReports}/>
      );
      node.instance().processDragStart(sampleReports[0]);
      node.instance().highlightIn(0,13);
      expect(node.instance().state.tilesRows[0][13].highlighted).toEqual(true);
      node.instance().processDrop({row: 0, col: 13});
      expect(node.instance().state.tilesRows[0][13].highlighted).toEqual(false);
      expect(moveMock).toHaveBeenCalled();
    })

    it('should pass old report if there was a conflict', () => {
      const node = mount(<EditGrid
        onReportSelected={selectionMock}
        onReportMoved={moveMock}
        onReportRemoved={removeMock}
        reports={sampleReports}/>
      );
      node.instance().processDragStart(sampleReports[0]);
      node.instance().highlightIn(0,15);
      node.instance().processDrop({row: 0, col: 15});
      expect(node.instance().state.tilesRows[0][13].highlighted).toEqual(false);

      const afterUpdate = JSON.parse(JSON.stringify(sampleReports[0]));
      afterUpdate.position.x = 0;
      afterUpdate.position.y = 15;
      expect(moveMock).toBeCalledWith(sampleReports[0], afterUpdate);
    })
  })

});
