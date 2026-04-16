import pc from "picocolors";
const DEFAULT_SIGNS = [" ", "⚠", "✖"];
const DEFAULT_COLORS = ["white", "yellow", "red"];
export function format(report = {}, options = {}) {
    const { results = [] } = report;
    const fi = (result) => formatInput(result, options);
    const fr = (result) => formatResult(result, options);
    return results
        .filter((r) => Array.isArray(r.warnings) || Array.isArray(r.errors))
        .map((result) => [...fi(result), ...fr(result)])
        .reduce((acc, item) => (Array.isArray(item) ? [...acc, ...item] : [...acc, item]), [])
        .join("\n");
}
function formatInput(result, options = {}) {
    const { color: enabled = true } = options;
    const { errors = [], warnings = [], input = "" } = result;
    if (!input) {
        return [""];
    }
    const sign = "⧗";
    const decoration = enabled ? pc.gray(sign) : sign;
    const decoratedInput = enabled ? pc.bold(input) : input;
    const hasProblems = errors.length > 0 || warnings.length > 0;
    return options.verbose || hasProblems
        ? [`${decoration}   input: ${decoratedInput}`]
        : [];
}
export function formatResult(result = {}, options = {}) {
    const { signs = DEFAULT_SIGNS, colors = DEFAULT_COLORS, color: enabled = true, } = options;
    const { errors = [], warnings = [] } = result;
    const problems = [...errors, ...warnings].map((problem) => {
        const sign = signs[problem.level] || "";
        const colorName = colors[problem.level] || "white";
        const colorFn = pc[colorName];
        const decoration = enabled ? colorFn(sign) : sign;
        const name = enabled ? pc.gray(`[${problem.name}]`) : `[${problem.name}]`;
        return `${decoration}   ${problem.message} ${name}`;
    });
    const sign = selectSign(result);
    const colorName = selectColor(result);
    const deco = enabled ? pc[colorName](sign) : sign;
    const el = errors.length;
    const wl = warnings.length;
    const hasProblems = problems.length > 0;
    const summary = options.verbose || hasProblems
        ? `${deco}   found ${el} problems, ${wl} warnings`
        : undefined;
    const fmtSummary = enabled && typeof summary === "string" ? pc.bold(summary) : summary;
    const help = hasProblems && options.helpUrl
        ? `ⓘ   Get help: ${options.helpUrl}`
        : undefined;
    return [
        ...problems,
        hasProblems ? "" : undefined,
        fmtSummary,
        help,
        hasProblems ? "" : undefined,
    ].filter((line) => typeof line === "string");
}
export default format;
function selectSign(result) {
    if ((result.errors || []).length > 0) {
        return "✖";
    }
    return (result.warnings || []).length ? "⚠" : "✔";
}
function selectColor(result) {
    if ((result.errors || []).length > 0) {
        return "red";
    }
    return (result.warnings || []).length ? "yellow" : "green";
}
//# sourceMappingURL=format.js.map