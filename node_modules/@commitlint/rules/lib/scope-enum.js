import * as ensure from "@commitlint/ensure";
import message from "@commitlint/message";
export const scopeEnum = ({ scope }, when = "always", value = []) => {
    const scopes = Array.isArray(value) ? value : value.scopes;
    if (!scope || !scopes.length) {
        return [true, ""];
    }
    const delimiters = Array.isArray(value) || !value.delimiters?.length
        ? ["/", "\\", ","]
        : value.delimiters;
    const delimiterPatterns = delimiters.map((delimiter) => {
        return delimiter === ","
            ? ", ?"
            : delimiter.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    });
    const messageScopes = scope.split(new RegExp(delimiterPatterns.join("|")));
    const errorMessage = ["scope must", `be one of [${scopes.join(", ")}]`];
    const isScopeInEnum = (scope) => ensure.enum(scope, scopes);
    let isValid;
    if (when === "never") {
        isValid = !messageScopes.some(isScopeInEnum) && !isScopeInEnum(scope);
        errorMessage.splice(1, 0, "not");
    }
    else {
        isValid = messageScopes.every(isScopeInEnum) || isScopeInEnum(scope);
    }
    return [isValid, message(errorMessage)];
};
//# sourceMappingURL=scope-enum.js.map