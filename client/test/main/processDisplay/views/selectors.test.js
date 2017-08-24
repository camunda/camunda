import {expect} from 'chai';
import {getInstanceCount, getDiagramXML} from 'main/processDisplay/views/selectors';

describe('main/processDisplay/views/selectors getInstanceCount', () => {
  it('should return piCount', () => {
    const state = {
      heatmap: {
        data: {
          piCount: 34
        }
      }
    };

    expect(getInstanceCount(state)).to.eql(34);
  });
});

describe('main/processDisplay/views/selectors getDiagramXML', () => {
  it('should bpmnXml data', () => {
    const state = {
      bpmnXml: {
        data: 'diagram'
      }
    };

    expect(getDiagramXML(state)).to.eql('diagram');
  });
});
