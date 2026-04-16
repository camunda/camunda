import * as ensure from "@commitlint/ensure";
import message from "@commitlint/message";
export const scopeDelimiterStyle = ({ scope }, when = "always", value = []) => {
    if (!scope) {
        return [true];
    }
    const delimiters = value.length ? value : ["/", "\\", ","];
    const scopeRawDelimiters = scope.match(/[^A-Za-z0-9-_]+/g) ?? [];
    const scopeDelimiters = [
        ...new Set(scopeRawDelimiters.map((delimiter) => {
            return delimiter.trim() === "," ? "," : delimiter;
        })),
    ];
    const isAllDelimitersAllowed = scopeDelimiters.every((delimiter) => {
        return ensure.enum(delimiter, delimiters);
    });
    const isNever = when === "never";
    return [
        isNever ? !isAllDelimitersAllowed : isAllDelimitersAllowed,
        message([
            `scope delimiters must ${isNever ? "not " : ""}be one of [${delimiters.join(", ")}]`,
        ]),
    ];
};
//# sourceMappingURL=scope-delimiter-style.js.map