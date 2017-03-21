import {expect} from 'chai';
import sinon from 'sinon';
import generateHeatmap from 'main/processDisplay/diagram/heatmap/heatmap_generator';

describe('Heatmap Generator', () => {
  const BOUNDING_BOX = {
    x: 0,
    y: 0,
    width: 100,
    height: 100
  };

  const SAMPLE_ELEMENT = {
    x: 20,
    y: 20,
    width: 50,
    height: 30,
    incoming: []
  };

  const viewer = {
    get: sinon.stub().returns({
      getDefaultLayer: sinon.stub().returns({
        getBBox: sinon.stub().returns(BOUNDING_BOX)
      }),
      get: sinon.stub().returns(SAMPLE_ELEMENT)
    })
  };

  const heatmapData = {
    act1: 1,
    act2: 3
  };

  let dimensions;
  let img;

  beforeEach(() => {
    ({dimensions, img} = generateHeatmap(viewer, heatmapData));
  });

  it('should return an image and a dimensions object', () => {
    expect(dimensions).to.exist;
    expect(img).to.exist;
  });

  describe('dimensions', () => {
    it('should have height, width and position', () => {
      const {x, y, width, height} = dimensions;

      expect(typeof x).to.be.eql('number');
      expect(typeof y).to.be.eql('number');
      expect(typeof width).to.be.eql('number');
      expect(typeof height).to.be.eql('number');
    });
  });

  describe('image', () => {
    it('should be a base64 encoded png string', () => {
      expect(typeof img).to.be.eql('string');
      expect(img).to.match(/^data:image\/png;base64,/);
    });
  });
});
