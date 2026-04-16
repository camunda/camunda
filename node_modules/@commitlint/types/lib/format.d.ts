import type pc from "picocolors";
import { QualifiedRules } from "./load.js";
import { RuleConfigSeverity } from "./rules.js";
export type Formatter = (report: FormattableReport, options: FormatOptions) => string;
export interface FormattableProblem {
    level: RuleConfigSeverity;
    name: keyof QualifiedRules;
    message: string;
}
export interface FormattableResult {
    errors?: FormattableProblem[];
    warnings?: FormattableProblem[];
}
export interface WithInput {
    input?: string;
}
export interface FormattableReport {
    results?: (FormattableResult & WithInput)[];
}
export type PicocolorsColor = Exclude<keyof typeof pc, "isColorSupported" | "createColors">;
export type ChalkColor = PicocolorsColor;
export interface FormatOptions {
    color?: boolean;
    signs?: readonly [string, string, string];
    colors?: readonly [PicocolorsColor, PicocolorsColor, PicocolorsColor];
    verbose?: boolean;
    helpUrl?: string;
}
//# sourceMappingURL=format.d.ts.map