import type { Parser } from "@commitlint/types";
import { type Commit, type ParserOptions } from "conventional-commits-parser";
export declare function parse(message: string, parser?: Parser, parserOpts?: ParserOptions): Promise<Commit>;
export default parse;
//# sourceMappingURL=index.d.ts.map