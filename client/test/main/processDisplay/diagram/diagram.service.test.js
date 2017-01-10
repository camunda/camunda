import {expect} from 'chai';
import sinon from 'sinon';
import {setupPromiseMocking} from 'testHelpers';
import {getHeatmap, __set__, __ResetDependency__} from 'main/processDisplay/diagram/diagram.service';

describe('Diagram service', () => {
  const heatmap = {
    dimensions: {
      x: 0,
      y: 0,
      width: 100,
      height: 100
    },
    img: 'base64-encoded image'
  };
  const heatmapData = {
    act1: 1,
    act2: 2
  };
  const processInstanceId = 'p1';
  const viewer = {};

  let generateHeatmap,
      getHeatmapData,
      response;

  setupPromiseMocking();

  beforeEach(() => {
    generateHeatmap = sinon.stub().returns(heatmap);
    __set__('generateHeatmap', generateHeatmap);

    getHeatmapData = sinon.stub().returns(Promise.resolve(heatmapData));
    __set__('getHeatmapData', getHeatmapData);

    getHeatmap(viewer, processInstanceId).then(result => response = result);
    Promise.runAll();
  });

  afterEach(() => {
    __ResetDependency__('generateHeatmap');
    __ResetDependency__('getHeatmapData');
  });

  it('should load the heatmap data', () => {
    expect(getHeatmapData.calledWith(processInstanceId)).to.eql(true);
  });

  it('should resolve promise with an svg image node containing the heatmap', () => {
    expect(response instanceof SVGImageElement).to.eql(true);
    expect(response.href.baseVal).to.eql(heatmap.img);
  });
});
