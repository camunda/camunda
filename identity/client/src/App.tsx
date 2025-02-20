import { FC, StrictMode } from "react";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import { getBaseUrl } from "./configuration";
import AppRoot from "./components/global/AppRoot";
import GlobalRoutes from "src/components/global/GlobalRoutes";
import { LoginPage } from "src/pages/login/LoginPage.tsx";
import Error403 from "src/pages/errors/403.tsx";

const App: FC = () => (
  <BrowserRouter basename={getBaseUrl()}>
    <StrictMode>
      <Routes>
        <Route key="login" path="/login" Component={LoginPage} />
        <Route path="/403" element={<Error403 />} />
        <Route
          key="identity-ui"
          path="*"
          element={
            <AppRoot>
              <GlobalRoutes />
            </AppRoot>
          }
        />
      </Routes>
    </StrictMode>
  </BrowserRouter>
);

export default App;
