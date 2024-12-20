import {FC, StrictMode} from "react";
import {BrowserRouter} from "react-router-dom";
import {getBaseUrl} from "./configuration";
import AppRoot from "./components/global/AppRoot";
import GlobalRoutes from "src/components/global/GlobalRoutes";

const App: FC = () =>
    <BrowserRouter basename={getBaseUrl()}>
      <StrictMode>
        <AppRoot>
          <GlobalRoutes/>
        </AppRoot>
      </StrictMode>
    </BrowserRouter>;

export default App;
