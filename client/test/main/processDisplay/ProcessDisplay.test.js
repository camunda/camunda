import {jsx} from 'view-utils';
import {mountTemplate, createMockComponent} from 'testHelpers';
import {expect} from 'chai';
import sinon from 'sinon';
import {ProcessDisplay, __set__, __ResetDependency__} from 'main/processDisplay/ProcessDisplay';

describe('<ProcessDisplay>', () => {
  let Controls;
  let ViewsArea;
  let ViewsDiagramArea;
  let getDefinitionId;
  let loadDiagram;
  let loadData;
  let resetStatisticData;
  let node;

  beforeEach(() => {
    Controls = createMockComponent('Controls', true);
    ViewsArea = createMockComponent('ViewsArea');
    ViewsDiagramArea = createMockComponent('ViewsDiagramArea');
    getDefinitionId = 'getDefinitionId';
    loadDiagram = sinon.spy();
    loadData = sinon.spy();
    resetStatisticData = sinon.spy();

    __set__('Controls', Controls);
    __set__('ViewsArea', ViewsArea);
    __set__('ViewsDiagramArea', ViewsDiagramArea);
    __set__('getDefinitionId', getDefinitionId);
    __set__('loadDiagram', loadDiagram);
    __set__('loadData', loadData);
    __set__('resetStatisticData', resetStatisticData);

    ({node} = mountTemplate(<ProcessDisplay />));
  });

  afterEach(() => {
    __ResetDependency__('Controls');
    __ResetDependency__('ViewsArea');
    __ResetDependency__('ViewsDiagramArea');
    __ResetDependency__('getDefinitionId');
    __ResetDependency__('loadDiagram');
    __ResetDependency__('loadData');
    __ResetDependency__('resetStatisticData');
  });

  it('should load diagram', () => {
    expect(loadDiagram.called).to.eql(true);
  });

  describe('Controls', () => {
    it('should display controls', () => {
      expect(node).to.contain.text(Controls.text);
    });

    it('should use correct selector', () => {
      const selector = Controls.getAttribute('selector');

      expect(selector({
        views: 'views',
        controls: {
          a: 1
        }
      })).to.eql({
        a: 1,
        views: 'views'
      });
    });

    it('should include ViewsArea', () => {
      const controlsNode = Controls.getChildrenNode();

      expect(controlsNode).to.contain.text(ViewsArea.text);
      expect(ViewsArea.appliedWith({areaComponent: 'Controls'})).to.eql(true);
    });

    it('should load data and reset statistics data on criteria change', () => {
      const onCriteriaChanged = Controls.getAttribute('onCriteriaChanged');

      onCriteriaChanged('criteria');

      expect(loadData.calledWith('criteria')).to.eql(true);
      expect(resetStatisticData.called).to.eql(true);
    });
  });

  it('should display ViewsDiagramArea', () => {
    expect(node).to.contain.text(ViewsDiagramArea.text);
  });

  it('should add Additional ViewsArea', () => {
    expect(ViewsArea.appliedWith({areaComponent: 'Additional'})).to.eql(true);
  });
});
