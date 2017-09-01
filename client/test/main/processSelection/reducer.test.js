import {expect} from 'chai';
import {reducer, createSetVersionAction, createSetVersionXmlAction} from 'main/processSelection/reducer';

describe('processSelection reducer', () => {
  const previousId = 'id-1';
  const version = 3;
  const xml = 'xml';
  let inputState;

  beforeEach(() => {
    inputState = {
      processDefinitions: {
        data: {
          list: [
            {
              current: {
                id: previousId
              },
              versions: [
                {
                  version: 3442
                },
                {
                  version,
                  a: 1
                }
              ]
            }
          ]
        }
      }
    };
  });

  it('should be possible to set version in selected process definition', () => {
    const resultState = reducer(
      inputState,
      createSetVersionAction(previousId, version)
    );

    expect(resultState).to.eql({
      processDefinitions: {
        data: {
          list: [
            {
              current: {
                version,
                a: 1
              },
              versions: [
                {
                  version: 3442
                },
                {
                  version,
                  a: 1
                }
              ]
            }
          ]
        }
      }
    });
  });

  it('should be possible to set xml for version', () => {
    const resultState = reducer(
      inputState,
      createSetVersionXmlAction(previousId, version, xml)
    );

    expect(resultState).to.eql({
      processDefinitions: {
        data: {
          list: [
            {
              current: {
                id: previousId
              },
              versions: [
                {
                  version: 3442
                },
                {
                  version,
                  bpmn20Xml: xml,
                  a: 1
                }
              ]
            }
          ]
        }
      }
    });
  });
});
