import { CommitParser, } from "conventional-commits-parser";
// @ts-expect-error -- no typings
import defaultChangelogOpts from "conventional-changelog-angular";
const defaultParser = (message, options) => {
    if (message === undefined || message === null) {
        throw new TypeError("Expected a raw commit");
    }
    const parser = new CommitParser(options);
    const result = parser.parse(message);
    result.scope = result.scope ?? null;
    result.subject = result.subject ?? null;
    result.type = result.type ?? null;
    return result;
};
export async function parse(message, parser = defaultParser, parserOpts) {
    const preset = await defaultChangelogOpts();
    const defaultOpts = preset.parser || preset.parserOpts;
    // Support user-provided parser options passed either flat or nested under a 'parser' key
    const userOpts = parserOpts?.parser || parserOpts || {};
    const opts = {
        ...defaultOpts,
        fieldPattern: null,
        ...userOpts,
    };
    const parsed = parser(message, opts);
    parsed.raw = message;
    return parsed;
}
export default parse;
//# sourceMappingURL=index.js.map