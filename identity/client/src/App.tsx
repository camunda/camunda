import { FC, StrictMode } from "react";
import { BrowserRouter } from "react-router-dom";
import { baseUrl } from "./configuration";
import AppRoot from "./components/global/AppRoot";
import GlobalRoutes from "src/components/global/GlobalRoutes";

const App: FC = () => (
  <BrowserRouter basename={baseUrl}>
    <StrictMode>
      <AppRoot>
        <GlobalRoutes />
      </AppRoot>
    </StrictMode>
  </BrowserRouter>
);

export default App;
