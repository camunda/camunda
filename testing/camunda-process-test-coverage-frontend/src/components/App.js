import React, {useState} from 'react';
import Tree from './Tree/Tree';
import DetailsPane from './Views/DetailsPane';
import COVERAGE_DATA_IMPORT from '../data/coverageData';

const COVERAGE_DATA = window.COVERAGE_DATA || COVERAGE_DATA_IMPORT;

function App() {
    const [selected, setSelected] = useState(null);
    const [tab, setTab] = useState(null);

    return (
        <div className="container-fluid h-100 coverage-app">
            <div className="row">
              <div className="col">
                <h1 className="m-0 py-3">
                  <img
                      src="./static/media/camunda-logo.png"
                      alt=""
                      width="36"
                      height="36"
                      className="me-2"
                  />
                  Camunda Process Test: Coverage
                  Report</h1>
              </div>
            </div>
          <div className="row h-100 d-flex flex-nowrap">
            <div className="col-auto border-end">
            <Tree
                        data={COVERAGE_DATA}
                        onSelect={(tname, node) => {
                            setTab(tname);
                            setSelected(node);
                        }}
                    />
                </div>
                <div className="col m-3 py-3">
                  <DetailsPane data={COVERAGE_DATA} type={tab} node={selected}
                               onSelect={(tname, node) => {
                                 setTab(tname);
                                 setSelected(node);
                               }}/>
                </div>
            </div>
        </div>
    );
}

export default App;
