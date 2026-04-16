import { PluginRecords } from "@commitlint/types";
export interface LoadPluginOptions {
    debug?: boolean;
    searchPaths?: string[];
}
export default function loadPlugin(plugins: PluginRecords, pluginName: string, options?: LoadPluginOptions | boolean): Promise<PluginRecords>;
//# sourceMappingURL=load-plugin.d.ts.map