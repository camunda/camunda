import {vitePlugin as remix} from '@remix-run/dev';
import {defineConfig} from 'vite';
import tsconfigPaths from 'vite-tsconfig-paths';
import {cjsInterop} from 'vite-plugin-cjs-interop';

export default defineConfig({
  plugins: [
    remix({
      future: {
        v3_fetcherPersist: true,
        v3_relativeSplatPath: true,
        v3_throwAbortReason: true,
      },
      ssr: false,
    }),
    tsconfigPaths(),
    // cjsInterop({
    //   dependencies: ['@carbon/react/icons', '@carbon/icons-react'],
    // }),
  ],
});
